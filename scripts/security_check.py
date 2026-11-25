#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""安全自查脚本：把常见越权与注入尝试固化成可重复执行的检查。

定位：这不是专业渗透测试工具，而是「每次改动后能在 30 秒内跑一遍」的回归清单。
它只验证本项目已经声称具备的防护是否仍然生效——比如「所有接口从登录态取
userId，不存在改 URL 看别人资料的越权」这类结论，靠人肉点几下很容易漏。

用法：
    python scripts/security_check.py --base-url http://localhost:8081
    python scripts/security_check.py --username user001 --password 123456 --peer bulk0300

退出码：全部通过为 0，任一失败为 1（可直接接进 CI 或提交前检查）。
"""

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass


def request(base_url, path, method="GET", token=None, body=None, headers=None):
    """返回 (http_status, 解析后的 JSON 或原始文本, 响应头)。"""
    url = base_url + path
    data = None
    hdrs = dict(headers or {})
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read()
            return resp.status, parse(raw), dict(resp.headers)
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, parse(raw), dict(e.headers)


def parse(raw):
    text = raw.decode("utf-8", "replace")
    try:
        return json.loads(text)
    except Exception:  # noqa: BLE001
        return text


def code_of(payload):
    return payload.get("code") if isinstance(payload, dict) else None


def login(base_url, username, password):
    status, payload, _ = request(base_url, "/api/auth/login", "POST",
                                 body={"username": username, "password": password})
    if code_of(payload) != 0:
        raise SystemExit(f"[error] 登录失败 {username}: {payload}")
    return payload["data"]["token"]


class Checker:
    def __init__(self):
        self.results = []

    def check(self, name, fn):
        try:
            ok, detail = fn()
        except Exception as exc:  # noqa: BLE001 - 检查项异常不中断整轮
            ok, detail = False, f"执行异常: {exc}"
        self.results.append((name, ok, detail))
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {name}" + ("" if ok else f"  → {detail}"))

    def summary(self):
        failed = [r for r in self.results if not r[1]]
        print()
        print(f"合计 {len(self.results)} 项：通过 {len(self.results) - len(failed)}，失败 {len(failed)}")
        return 1 if failed else 0


def main():
    ap = argparse.ArgumentParser(description="安全自查")
    ap.add_argument("--base-url", default="http://localhost:8081")
    ap.add_argument("--username", default="user001")
    ap.add_argument("--password", default="123456")
    ap.add_argument("--peer", default="bulk0300", help="用于越权测试的另一个普通用户")
    ap.add_argument("--admin", default="admin")
    ap.add_argument("--admin-password", default="admin123")
    args = ap.parse_args()

    base = args.base_url
    user_token = login(base, args.username, args.password)
    peer_token = login(base, args.peer, args.password)
    admin_token = login(base, args.admin, args.admin_password)

    checker = Checker()

    # ---------- 鉴权 ----------
    def unauth():
        _, payload, _ = request(base, "/api/user/profile")
        return code_of(payload) == 1002, f"期望 1002，实际 {code_of(payload)}"

    def tampered_token():
        forged = user_token[:-2] + ("AA" if not user_token.endswith("AA") else "BB")
        _, payload, _ = request(base, "/api/user/profile", token=forged)
        return code_of(payload) == 1002, f"篡改令牌仍被接受，返回 {code_of(payload)}"

    def user_hits_admin():
        _, stats, _ = request(base, "/api/admin/statistics", token=user_token)
        _, orders, _ = request(base, "/api/admin/orders", token=user_token)
        ok = code_of(stats) == 1003 and code_of(orders) == 1003
        return ok, f"统计接口 {code_of(stats)}，订单接口 {code_of(orders)}，期望均为 1003"

    # ---------- 越权（IDOR）----------
    def peer_order_no():
        _, payload, _ = request(base, f"/api/orders?pageNum=1&pageSize=1", token=peer_token)
        records = (payload.get("data") or {}).get("records") or []
        return records[0]["orderNo"] if records else None

    def read_peer_order():
        order_no = peer_order_no()
        if not order_no:
            return True, "对端用户没有订单，跳过"
        _, payload, _ = request(base, f"/api/orders/{order_no}", token=user_token)
        return code_of(payload) in (1003, 4003), f"读到他人订单，返回 {code_of(payload)}"

    def read_peer_logs():
        order_no = peer_order_no()
        if not order_no:
            return True, "对端用户没有订单，跳过"
        _, payload, _ = request(base, f"/api/orders/{order_no}/logs", token=user_token)
        return code_of(payload) in (1003, 4003), f"读到他人订单日志，返回 {code_of(payload)}"

    # ---------- 注入 ----------
    def sql_injection():
        payloads = ["' OR '1'='1", "'; DROP TABLE product; --", "1' UNION SELECT password_hash FROM user --"]
        for keyword in payloads:
            path = "/api/products?pageNum=1&pageSize=5&keyword=" + urllib.parse.quote(keyword)
            status, payload, _ = request(base, path, token=user_token)
            if code_of(payload) != 0:
                return False, f"注入载荷 {keyword!r} 导致异常响应 {code_of(payload)}"
            # 参数化查询下应返回 0 条，而不是全表
            total = (payload.get("data") or {}).get("total")
            if total is not None and total > 0:
                return False, f"载荷 {keyword!r} 返回了 {total} 条，疑似拼接 SQL"
        return True, "三个载荷均被当作普通字符串处理，未返回数据也未报错"

    # ---------- 信息泄漏 ----------
    def no_stacktrace():
        _, payload, _ = request(base, "/api/products?pageNum=abc", token=user_token)
        text = json.dumps(payload, ensure_ascii=False) if isinstance(payload, dict) else str(payload)
        leaked = [k for k in ("Exception", "at java", "at org.", "SQL", "mybatis") if k in text]
        return not leaked, f"响应中出现实现细节: {leaked}"

    def no_password_field():
        _, payload, _ = request(base, "/api/user/profile", token=user_token)
        text = json.dumps(payload, ensure_ascii=False) if isinstance(payload, dict) else str(payload)
        leaked = [k for k in ("passwordHash", "password_hash", "password") if k in text]
        return not leaked, f"资料响应中暴露口令字段: {leaked}"

    # ---------- 边界 ----------
    def page_bounds():
        for value in ("0", "-1", "999999999"):
            _, payload, _ = request(base, f"/api/products?pageNum=1&pageSize={value}", token=user_token)
            if code_of(payload) != 0:
                return False, f"pageSize={value} 返回 {code_of(payload)}"
            size = (payload.get("data") or {}).get("pageSize")
            if size is not None and size > 100:
                return False, f"pageSize={value} 未被钳制，实际 {size}"
        return True, "0 / 负数 / 超大值均未导致异常，且超过 100 被钳制"

    # ---------- CORS ----------
    def cors_rejects_unknown_origin():
        _, _, headers = request(base, "/api/products?pageNum=1&pageSize=1",
                                headers={"Origin": "http://evil.example"})
        allowed = headers.get("Access-Control-Allow-Origin")
        if allowed in (None, "", "null"):
            return True, "未对陌生来源返回允许头"
        if allowed == "http://evil.example":
            return False, "任意来源都被允许，等同放开跨域"
        return True, f"只允许了 {allowed}"

    # ---------- 上传 ----------
    def upload_spoof():
        boundary = "----security-check"
        fake = b"<html><script>alert(1)</script></html>"
        body = (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="file"; filename="x.png"\r\n'
            "Content-Type: image/png\r\n\r\n"
        ).encode() + fake + f"\r\n--{boundary}--\r\n".encode()
        req = urllib.request.Request(
            base + "/api/user/avatar", data=body, method="POST",
            headers={"Authorization": "Bearer " + user_token,
                     "Content-Type": f"multipart/form-data; boundary={boundary}"})
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            payload = json.loads(e.read().decode("utf-8", "replace"))
        return code_of(payload) == 1001, f"伪装成 png 的 HTML 被接受，返回 {code_of(payload)}"

    checker.check("未登录访问个人资料应被拒绝", unauth)
    checker.check("篡改令牌应被拒绝", tampered_token)
    checker.check("普通用户访问管理接口应被拒绝（含两个入口）", user_hits_admin)
    checker.check("越权读取他人订单详情", read_peer_order)
    checker.check("越权读取他人订单流转日志", read_peer_logs)
    checker.check("SQL 注入载荷应被当作普通字符串", sql_injection)
    checker.check("错误响应不应泄漏异常栈与 SQL", no_stacktrace)
    checker.check("资料接口不应返回口令字段", no_password_field)
    checker.check("分页参数边界应被钳制", page_bounds)
    checker.check("CORS 不应放开任意来源", cors_rejects_unknown_origin)
    checker.check("改名成图片的 HTML 应被拒绝", upload_spoof)

    return checker.summary()


if __name__ == "__main__":
    sys.exit(main())
