#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""并发压测脚本：零依赖（仅标准库），用于量化优化前后的差别。

为什么自己写而不引 k6/JMeter：本项目面向学习复现，多一个运行时依赖就多一道
复现门槛。压测本质上只是「N 个线程循环打请求 + 统计延迟分布」，
标准库足够，且结果可解释。

用法：
    # 未登录的公开接口
    python scripts/loadtest.py --path "/api/products?pageNum=1&pageSize=20" -c 20 -d 10

    # 需要登录的接口（自动用账号登录拿令牌）
    python scripts/loadtest.py --username user001 --password 123456 \
        --path "/api/recommend/home?topN=10" --path "/api/orders?pageNum=1&pageSize=5" -c 10 -d 10

输出：每条路径的请求数、QPS、P50/P95/P99 延迟与失败率。
失败率非 0 时退出码为 1，便于接到 CI 或批处理里做门禁。
"""

import argparse
import json
import re
import sys
import threading
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

# Windows 控制台默认不是 UTF-8，先兜住中文输出
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass

CODE_PATTERN = re.compile(rb'"code":\s*(\d+)')


def login(base_url, username, password):
    """登录换取 JWT，失败直接退出——压测不该在未鉴权状态下跑出好看的数字。"""
    payload = json.dumps({"username": username, "password": password}).encode("utf-8")
    req = urllib.request.Request(
        base_url + "/api/auth/login", data=payload,
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = resp.read()
    match = CODE_PATTERN.search(body)
    if not match or match.group(1) != b"0":
        raise SystemExit("[error] 登录失败，检查账号密码: " + body.decode("utf-8", "replace")[:200])
    token = json.loads(body)["data"]["token"]
    print(f"[info] 已登录 {username}，令牌长度 {len(token)}")
    return token


def worker(base_url, path, token, stop_at, latencies, errors):
    """单个线程：在截止时间前尽可能多地发请求。连接复用，避免把握手开销算进延迟。"""
    host = urllib.parse.urlparse(base_url)
    conn_host = host.hostname
    conn_port = host.port or (443 if host.scheme == "https" else 80)
    import http.client

    conn = None
    local_lat = []
    local_err = 0
    headers = {"Authorization": "Bearer " + token} if token else {}

    while time.time() < stop_at:
        try:
            if conn is None:
                conn = http.client.HTTPConnection(conn_host, conn_port, timeout=15)
            start = time.perf_counter()
            conn.request("GET", path, headers=headers)
            resp = conn.getresponse()
            body = resp.read()
            elapsed = (time.perf_counter() - start) * 1000.0

            local_lat.append(elapsed)
            # HTTP 层面 2xx 但业务码非 0（如登录态失效）也算失败，
            # 否则压测会得出「0 失败」的假象
            match = CODE_PATTERN.search(body)
            if resp.status >= 400 or (match and match.group(1) != b"0"):
                local_err += 1
        except Exception:  # noqa: BLE001 - 连接层异常同样计入失败
            local_err += 1
            try:
                if conn:
                    conn.close()
            finally:
                conn = None
            time.sleep(0.005)

    if conn:
        conn.close()
    latencies.extend(local_lat)
    errors[0] += local_err


def percentile(sorted_values, pct):
    if not sorted_values:
        return 0.0
    index = int(round((pct / 100.0) * (len(sorted_values) - 1)))
    return sorted_values[index]


def run(base_url, paths, token, concurrency, duration, warmup):
    results = []
    for path in paths:
        if warmup > 0:
            worker(base_url, path, token, time.time() + warmup, [], [0])

        latencies, errors = [], [0]
        stop_at = time.time() + duration
        started = time.time()
        with ThreadPoolExecutor(max_workers=concurrency) as pool:
            futures = [pool.submit(worker, base_url, path, token, stop_at, latencies, errors)
                       for _ in range(concurrency)]
            for future in futures:
                future.result()
        elapsed = time.time() - started

        latencies.sort()
        total = len(latencies) + errors[0]
        results.append({
            "path": path,
            "requests": total,
            "qps": total / elapsed if elapsed > 0 else 0.0,
            "p50": percentile(latencies, 50),
            "p95": percentile(latencies, 95),
            "p99": percentile(latencies, 99),
            "errors": errors[0],
        })
    return results


def main():
    ap = argparse.ArgumentParser(description="并发压测（零依赖）")
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--path", action="append", required=True, help="可重复指定多个路径")
    ap.add_argument("-c", "--concurrency", type=int, default=10)
    ap.add_argument("-d", "--duration", type=int, default=10, help="每个路径压测秒数")
    ap.add_argument("--warmup", type=int, default=2, help="预热秒数，避开 JIT 与缓存冷启动")
    ap.add_argument("--username")
    ap.add_argument("--password")
    ap.add_argument("--token", help="直接指定令牌，免登录")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    token = args.token
    if not token and args.username:
        token = login(args.base_url, args.username, args.password or "")

    results = run(args.base_url, args.path, token, args.concurrency, args.duration, args.warmup)

    if args.json:
        print(json.dumps({"baseUrl": args.base_url, "concurrency": args.concurrency,
                          "duration": args.duration, "results": results},
                         ensure_ascii=False, indent=2))
    else:
        print()
        print(f"并发 {args.concurrency}，每路径 {args.duration}s"
              f"（预热 {args.warmup}s）")
        print(f"{'路径':<42}{'请求':>7}{'QPS':>9}{'P50':>9}{'P95':>9}{'P99':>9}{'失败':>7}")
        print("-" * 92)
        for r in results:
            print(f"{r['path']:<42}{r['requests']:>7}{r['qps']:>9.1f}"
                  f"{r['p50']:>8.1f}ms{r['p95']:>8.1f}ms{r['p99']:>8.1f}ms{r['errors']:>7}")
        print("-" * 92)
        print("说明：P50/P95/P99 为端到端延迟（复用连接，含服务端处理与网络往返）")

    return 1 if any(r["errors"] > 0 for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
