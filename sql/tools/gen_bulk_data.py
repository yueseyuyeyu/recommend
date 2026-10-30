# -*- coding: utf-8 -*-
"""按类目批量生成商品 / 用户 / 行为数据。

设计要点：
1. 商品按三级类目均匀铺开，名称由「类目名 + 款式 + 序号」拼成，价格按一级类目定档，
   避免出现「500 个商品叫演示商品 N」这种看不出品类的东西。
2. 行为不是随机撒的：每个用户先抽 1-2 个偏好一级类目，大部分行为落在偏好类目内，
   少量随机探索。这样协同过滤才有可学的模式，混合推荐才可能跑赢热门基线。
3. 主键由脚本显式分配（当前 MAX(id)+1 起），便于用 id 区间回滚，也让行为能直接引用商品 id。

用法：
    python gen_bulk_data.py                    # 默认规模
    python gen_bulk_data.py --per-leaf 40      # 每个三级类目 40 个商品
    python gen_bulk_data.py --users 1000
"""

import argparse
import io
import os
import random
import subprocess
from datetime import datetime, timedelta

MYSQL = r"D:\mysql\mysql-8.0.27-winx64\bin\mysql.exe"
DB = "recommend_db"
OUT = r"D:\桌面\商品推荐系统\sql\migration\V10__bulk_demo_data.sql"

# 一级类目的价格区间（元）
PRICE_BANDS = {
    "手机数码": (799, 6999),
    "电脑办公": (1299, 12999),
    "家用电器": (399, 8999),
    "服饰鞋包": (59, 1299),
    "美妆个护": (29, 899),
    "食品生鲜": (8, 199),
    "运动户外": (49, 1999),
    "图书文娱": (10, 299),
    "家居日用": (19, 1599),
    "母婴玩具": (29, 799),
}
DEFAULT_BAND = (50, 1000)

# 商品名里的款式词，让名字看起来像真商品
VARIANTS = ["精选款", "旗舰款", "入门款", "新款", "经典款",
            "Pro版", "Plus版", "轻奢款", "家用款", "专业款"]

# 行为类型与权重，与后端 BehaviorType 枚举保持一致
BEHAVIORS = [
    ("VIEW", 1.0, 0.55),
    ("CLICK", 2.0, 0.25),
    ("CART", 3.0, 0.12),
    ("PURCHASE", 5.0, 0.08),
]


def mysql_query(sql):
    """通过 mysql 客户端查询，返回去掉表头的行列表。"""
    result = subprocess.run(
        [MYSQL, "--default-character-set=utf8mb4", "-u", "root",
         "-p" + os.environ["DB_PASSWORD"], DB, "-N", "-e", sql],
        capture_output=True, text=True, encoding="utf-8"
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr)
    return [line.split("\t") for line in result.stdout.strip().split("\n") if line]


def sql_escape(text):
    """转义单引号，生成的是单引号字符串字面量。"""
    return text.replace("\\", "\\\\").replace("'", "''")


def load_leaf_categories():
    """取三级类目，附带所属二级与一级名称。"""
    rows = mysql_query(
        "SELECT c3.id, c3.name, c2.name, c1.id, c1.name "
        "FROM category c3 "
        "JOIN category c2 ON c2.id = c3.parent_id "
        "JOIN category c1 ON c1.id = c2.parent_id "
        "WHERE c3.parent_id > 10 ORDER BY c3.id;"
    )
    cats = []
    for r in rows:
        cats.append({
            "id": int(r[0]), "leaf": r[1], "mid": r[2],
            "top_id": int(r[3]), "top": r[4],
        })
    return cats


def next_ids(table):
    rows = mysql_query(f"SELECT COALESCE(MAX(id), 0) FROM `{table}`;")
    return int(rows[0][0]) + 1


def pick_behavior_type(rnd):
    r = rnd.random()
    acc = 0.0
    for code, weight, prob in BEHAVIORS:
        acc += prob
        if r <= acc:
            return code, weight
    return BEHAVIORS[-1][0], BEHAVIORS[-1][1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--per-leaf", type=int, default=30, help="每个三级类目生成多少商品")
    parser.add_argument("--users", type=int, default=500, help="生成多少新用户")
    parser.add_argument("--min-behaviors", type=int, default=25)
    parser.add_argument("--max-behaviors", type=int, default=60)
    parser.add_argument("--seed", type=int, default=20261030)
    args = parser.parse_args()

    rnd = random.Random(args.seed)

    cats = load_leaf_categories()
    if not cats:
        raise SystemExit("没有读到三级类目，请先执行 V5 迁移")

    product_start = next_ids("product")
    user_start = next_ids("user")
    behavior_start = next_ids("behavior")

    lines = []
    add = lines.append

    add("-- =============================================================")
    add("-- V10 批量演示数据：按三级类目铺开的商品 / 用户 / 行为")
    add("-- 本文件由 sql/tools/gen_bulk_data.py 生成，请勿手改")
    add("--")
    add("-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）")
    add("--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \\")
    add("--     < sql/migration/V10__bulk_demo_data.sql")
    add("--")
    add(f"-- 规模：商品 {len(cats) * args.per_leaf}（{len(cats)} 个三级类目 × {args.per_leaf}），"
        f"用户 {args.users}，行为见文件末尾统计")
    add("--")
    add("-- 回滚（按 id 区间精确删除，不影响原有数据）：")
    add(f"--   DELETE FROM `behavior` WHERE id >= {behavior_start};")
    add(f"--   DELETE FROM `user`     WHERE id >= {user_start};")
    add(f"--   DELETE FROM `product`  WHERE id >= {product_start};")
    add("-- =============================================================")
    add("")
    add("USE recommend_db;")
    add("")

    # ---------------- 旧商品改名 ----------------
    # seed 里的 500 个商品叫「演示商品 N」，和按类目命名的新数据混在一起很违和，
    # 这里按它们实际挂的三级类目重命名。只改展示字段，不动 id 与引用关系。
    add("-- ---------- 0. 把 seed 里的「演示商品 N」改成按类目命名并重定价 ----------")
    add("-- 只改展示字段与价格，不动 id 与引用关系。")
    add("-- 必须重定价：seed 的价格是 10~5000 随机撒的，")
    add("-- 改完名会出现「苹果平板 ¥46.90」这种明显不合理的数据。")
    add("UPDATE `product` p")
    add("JOIN `category` c3 ON c3.`id` = p.`category_id`")
    add("JOIN `category` c2 ON c2.`id` = c3.`parent_id`")
    add("JOIN `category` c1 ON c1.`id` = c2.`parent_id`")
    add("SET p.`name`        = CONCAT(c3.`name`, ' 精选款 ', p.`id`),")
    add("    p.`subtitle`    = CONCAT(c1.`name`, ' · ', c2.`name`),")
    add("    p.`description` = CONCAT('本商品属于', c1.`name`, '下的', c2.`name`,")
    add("                           '分类，细分品类为', c3.`name`, '。'),")
    add("    p.`price` = ROUND(CASE c1.`name`")
    for top, (low, high) in PRICE_BANDS.items():
        add(f"        WHEN '{top}' THEN {low} + RAND() * {high - low}")
    add(f"        ELSE {DEFAULT_BAND[0]} + RAND() * {DEFAULT_BAND[1] - DEFAULT_BAND[0]} END, 2),")
    add("    p.`original_price` = ROUND(p.`price` * 1.25, 2)")
    add(f"WHERE p.`id` < {product_start};")
    add("")

    # ---------------- 商品 ----------------
    add("-- ---------- 1. 商品：每个三级类目均匀铺开 ----------")
    products = []  # (id, top_id, top_name)
    pid = product_start
    batch = []

    for cat in cats:
        low, high = PRICE_BANDS.get(cat["top"], DEFAULT_BAND)
        for i in range(args.per_leaf):
            variant = rnd.choice(VARIANTS)
            name = f"{cat['leaf']} {variant} {i + 1:03d}"
            subtitle = f"{cat['top']} · {cat['mid']}"
            price = round(rnd.uniform(low, high), 2)
            original = round(price * rnd.uniform(1.05, 1.45), 2)
            stock = rnd.randint(50, 999)
            # 让销量呈现长尾分布：少数爆款 + 大量长尾
            sales = int(rnd.paretovariate(1.6) * 30) if rnd.random() < 0.85 else rnd.randint(800, 3000)
            sales = min(sales, 5000)
            image = f"https://picsum.photos/seed/bulk{pid}/400/400"
            desc = (f"{name}，属于{cat['top']}下的{cat['mid']}分类。"
                    f"本商品为{cat['leaf']}类目的{variant}，"
                    f"适合日常使用，支持七天无理由退换。")
            batch.append(
                f"({pid},{cat['id']},'{sql_escape(name)}','{sql_escape(subtitle)}',"
                f"'{image}',{price},{original},{stock},{sales},1,'{sql_escape(desc)}',0,NOW(),NOW())"
            )
            products.append((pid, cat["top_id"], cat["top"]))
            pid += 1

            if len(batch) >= 200:
                add("INSERT INTO `product` (`id`,`category_id`,`name`,`subtitle`,`main_image`,"
                    "`price`,`original_price`,`stock`,`sales`,`status`,`description`,"
                    "`is_deleted`,`create_time`,`update_time`) VALUES")
                add(",\n".join(batch) + ";")
                batch = []

    if batch:
        add("INSERT INTO `product` (`id`,`category_id`,`name`,`subtitle`,`main_image`,"
            "`price`,`original_price`,`stock`,`sales`,`status`,`description`,"
            "`is_deleted`,`create_time`,`update_time`) VALUES")
        add(",\n".join(batch) + ";")
    add("")

    # ---------------- 用户 ----------------
    add(f"-- ---------- 2. 用户：新增 {args.users} 个（密码统一为 123456） ----------")
    password_hash = "$2a$10$8TKuT7SfLZjroCXlnqMi0eAyhWuawgDWkGo7bf1i3EwXtk7WdXy2e"
    ubatch = []
    user_ids = []
    for i in range(args.users):
        uid = user_start + i
        username = f"bulk{uid:04d}"
        nickname = f"用户{uid:04d}"
        email = f"{username}@example.com"
        ubatch.append(
            f"({uid},'{username}','{password_hash}','{nickname}','{email}',"
            f"NULL,NULL,NULL,1,1,NOW(),NOW(),NOW())"
        )
        user_ids.append(uid)
        if len(ubatch) >= 200:
            add("INSERT INTO `user` (`id`,`username`,`password_hash`,`nickname`,`email`,"
                "`phone`,`address`,`avatar`,`role`,`status`,`last_login_time`,"
                "`create_time`,`update_time`) VALUES")
            add(",\n".join(ubatch) + ";")
            ubatch = []
    if ubatch:
        add("INSERT INTO `user` (`id`,`username`,`password_hash`,`nickname`,`email`,"
            "`phone`,`address`,`avatar`,`role`,`status`,`last_login_time`,"
            "`create_time`,`update_time`) VALUES")
        add(",\n".join(ubatch) + ";")
    add("")

    # ---------------- 行为 ----------------
    add("-- ---------- 3. 行为：每个用户集中在自己偏好的类目上 ----------")
    by_top = {}
    for product_id, top_id, _ in products:
        by_top.setdefault(top_id, []).append(product_id)
    top_ids = list(by_top.keys())
    all_products = [p[0] for p in products]

    now = datetime.now()
    bbatch = []
    bid = behavior_start
    total_behaviors = 0

    for uid in user_ids:
        # 主偏好 + 可选次偏好，制造类目聚集
        primary = rnd.choice(top_ids)
        preferred = [primary]
        if rnd.random() < 0.35 and len(top_ids) > 1:
            secondary = rnd.choice([t for t in top_ids if t != primary])
            preferred.extend([secondary])

        pool = []
        for top in preferred:
            pool.extend(by_top[top])
        # 15% 概率探索偏好外的商品
        explore_ratio = 0.15

        count = rnd.randint(args.min_behaviors, args.max_behaviors)
        count = min(count, len(pool) + len(all_products))

        chosen = set()
        for _ in range(count):
            if rnd.random() < explore_ratio:
                target = rnd.choice(all_products)
            else:
                target = rnd.choice(pool)
            if target in chosen:
                continue
            chosen.add(target)

            code, weight = pick_behavior_type(rnd)
            # 时间分布：近 30 天内，越近越密集
            days_ago = rnd.random() ** 1.5 * 30
            created = now - timedelta(days=days_ago, seconds=rnd.randint(0, 86399))
            bbatch.append(
                f"({bid},{uid},{target},'{code}',{weight},NULL,"
                f"'{created.strftime('%Y-%m-%d %H:%M:%S')}')"
            )
            bid += 1
            total_behaviors += 1

            if len(bbatch) >= 500:
                add("INSERT INTO `behavior` (`id`,`user_id`,`product_id`,"
                    "`behavior_type`,`weight`,`rating`,`created_at`) VALUES")
                add(",\n".join(bbatch) + ";")
                bbatch = []
    if bbatch:
        add("INSERT INTO `behavior` (`id`,`user_id`,`product_id`,"
            "`behavior_type`,`weight`,`rating`,`created_at`) VALUES")
        add(",\n".join(bbatch) + ";")
    add("")

    add("-- ---------- 统计 ----------")
    add("SELECT (SELECT COUNT(*) FROM `product`)   AS products,")
    add("       (SELECT COUNT(*) FROM `user`)      AS users,")
    add("       (SELECT COUNT(*) FROM `behavior`)  AS behaviors,")
    add("       (SELECT COUNT(DISTINCT category_id) FROM `product`) AS used_categories;")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    print(f"已生成 {OUT}")
    print(f"  商品 {len(products)}（id {product_start} ~ {pid - 1}）")
    print(f"  用户 {args.users}（id {user_start} ~ {user_start + args.users - 1}）")
    print(f"  行为 {total_behaviors}（id {behavior_start} ~ {bid - 1}）")
    print(f"  文件行数 {len(lines)}")


if __name__ == "__main__":
    main()
