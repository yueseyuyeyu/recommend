-- =============================================================
-- 商品推荐系统 - 演示数据脚本（纯 SQL 版）
-- 数据库：MySQL 8.0    库名：recommend_db    依赖：sql/schema.sql 已执行
--
-- 与 SeedDataGenerator 等价：全部数据由确定性的算术规则推导，
-- 同一份脚本重复执行得到完全相同的数据，便于复现算法评测结果。
-- 与 Java 版的区别：这里不依赖 Maven / JDBC，只要能连上 MySQL 就能灌数据。
--
-- 用法（PowerShell）：
--   & "D:\mysql\mysql-8.0.27-winx64\bin\mysql.exe" -u root -p `
--       --default-character-set=utf8mb4 recommend_db -e "source sql/seed.sql"
--
-- 数据规模：类目 40（10 一级 + 30 二级）、商品 500、用户 201（含 admin）、
--           行为 5000、订单 800、订单明细约 1600、评价 600、购物车 3
-- 演示账号：admin / admin123（管理员），user001 / 123456（普通用户）
--
-- 警告：脚本会先 TRUNCATE 8 张业务表，请在演示库执行，不要对着生产库跑。
-- =============================================================

USE recommend_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `review`;
TRUNCATE TABLE `order_item`;
TRUNCATE TABLE `orders`;
TRUNCATE TABLE `cart`;
TRUNCATE TABLE `behavior`;
TRUNCATE TABLE `product`;
TRUNCATE TABLE `category`;
TRUNCATE TABLE `user`;

SET FOREIGN_KEY_CHECKS = 1;

-- -------------------------------------------------------------
-- 0. 数字辅助表：1..5000，后面所有批量插入都从它派生
--    临时表在同一语句里不能被引用两次（MySQL 错误 1137），
--    所以 10 行数字序列用 CTE 直接内联，不入临时表
-- -------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_seq;
CREATE TEMPORARY TABLE tmp_seq (n INT NOT NULL PRIMARY KEY);

INSERT INTO tmp_seq (n)
WITH digits AS (
    SELECT 0 AS d UNION ALL
    SELECT 1 UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5 UNION ALL
    SELECT 6 UNION ALL
    SELECT 7 UNION ALL
    SELECT 8 UNION ALL
    SELECT 9
)
SELECT a.d + b.d * 10 + c.d * 100 + e.d * 1000 + 1
FROM digits a, digits b, digits c, digits e
WHERE a.d + b.d * 10 + c.d * 100 + e.d * 1000 < 5000;

-- 订单内商品序号（1..3），避免与 5000 行的 tmp_seq 交叉连接
DROP TEMPORARY TABLE IF EXISTS tmp_item_seq;
CREATE TEMPORARY TABLE tmp_item_seq (n INT NOT NULL PRIMARY KEY);
INSERT INTO tmp_item_seq (n) VALUES (1), (2), (3);

-- -------------------------------------------------------------
-- 1. 类目：id 1-10 为一级，id 11-40 为二级（每个一级类目挂 3 个二级）
--    商品表的 category_id 依赖这套固定编号，请勿改序
-- -------------------------------------------------------------
INSERT INTO `category` (id, name, parent_id, sort_order) VALUES
                                                             (1,  '手机数码', 0, 1),
                                                             (2,  '电脑办公', 0, 2),
                                                             (3,  '家用电器', 0, 3),
                                                             (4,  '服饰鞋包', 0, 4),
                                                             (5,  '美妆个护', 0, 5),
                                                             (6,  '食品生鲜', 0, 6),
                                                             (7,  '运动户外', 0, 7),
                                                             (8,  '图书文娱', 0, 8),
                                                             (9,  '家居日用', 0, 9),
                                                             (10, '母婴玩具', 0, 10),
                                                             (11, '手机数码-旗舰', 1, 1), (12, '手机数码-热销', 1, 2), (13, '手机数码-新品', 1, 3),
                                                             (14, '电脑办公-旗舰', 2, 1), (15, '电脑办公-热销', 2, 2), (16, '电脑办公-新品', 2, 3),
                                                             (17, '家用电器-旗舰', 3, 1), (18, '家用电器-热销', 3, 2), (19, '家用电器-新品', 3, 3),
                                                             (20, '服饰鞋包-旗舰', 4, 1), (21, '服饰鞋包-热销', 4, 2), (22, '服饰鞋包-新品', 4, 3),
                                                             (23, '美妆个护-旗舰', 5, 1), (24, '美妆个护-热销', 5, 2), (25, '美妆个护-新品', 5, 3),
                                                             (26, '食品生鲜-旗舰', 6, 1), (27, '食品生鲜-热销', 6, 2), (28, '食品生鲜-新品', 6, 3),
                                                             (29, '运动户外-旗舰', 7, 1), (30, '运动户外-热销', 7, 2), (31, '运动户外-新品', 7, 3),
                                                             (32, '图书文娱-旗舰', 8, 1), (33, '图书文娱-热销', 8, 2), (34, '图书文娱-新品', 8, 3),
                                                             (35, '家居日用-旗舰', 9, 1), (36, '家居日用-热销', 9, 2), (37, '家居日用-新品', 9, 3),
                                                             (38, '母婴玩具-旗舰', 10, 1), (39, '母婴玩具-热销', 10, 2), (40, '母婴玩具-新品', 10, 3);

-- -------------------------------------------------------------
-- 2. 用户：id=1 管理员，id=2..201 普通用户
--    口令一律 BCrypt(10) 哈希，脚本内不出现明文口令
--    admin123 -> id=1，123456 -> id=2..201
-- -------------------------------------------------------------
INSERT INTO `user` (id, username, password_hash, nickname, email, role, status) VALUES
    (1, 'admin', '$2a$10$6QAOT/205z8uYK/WHQVAJOcIYIPmsrKqEhlu7w.9fk3AuhylMXCSa', '管理员', 'admin@example.com', 2, 1);

INSERT INTO `user` (id, username, password_hash, nickname, email, role, status)
SELECT 1 + s.n,
       CONCAT('user', LPAD(s.n, 3, '0')),
       '$2a$10$8TKuT7SfLZjroCXlnqMi0eAyhWuawgDWkGo7bf1i3EwXtk7WdXy2e',
       CONCAT('user', LPAD(s.n, 3, '0')),
       CONCAT('user', LPAD(s.n, 3, '0'), '@example.com'),
       1,
       1
FROM tmp_seq s
WHERE s.n <= 200;

-- -------------------------------------------------------------
-- 3. 商品：id = 1..500，category_id = 11 + (id mod 30)
--    价格、库存、销量全部由 id 推导，保证可复现
-- -------------------------------------------------------------
INSERT INTO `product` (id, category_id, name, subtitle, main_image, price, original_price,
                       stock, sales, status, description)
SELECT s.n,
       11 + MOD(s.n, 30),
       CONCAT('演示商品 ', s.n),
       CONCAT(
               ELT(1 + FLOOR(MOD(s.n, 30) / 3),
                   '手机数码', '电脑办公', '家用电器', '服饰鞋包', '美妆个护',
                   '食品生鲜', '运动户外', '图书文娱', '家居日用', '母婴玩具'),
               ' · ',
               ELT(1 + MOD(MOD(s.n, 30), 3), '旗舰', '热销', '新品')
       ),
       CONCAT('https://picsum.photos/seed/product', s.n, '/400/400'),
       CAST(ROUND(9.9 + MOD(s.n * 37, 4990), 2) AS DECIMAL(10, 2)),
       CAST(ROUND((9.9 + MOD(s.n * 37, 4990)) * 1.2, 2) AS DECIMAL(10, 2)),
       50 + MOD(s.n * 53, 950),
       MOD(s.n * 17, 500),
       1,
       CONCAT('用于演示推荐效果的种子商品，编号 ', s.n)
FROM tmp_seq s
WHERE s.n <= 500;

-- -------------------------------------------------------------
-- 4. 行为：5000 条，五类行为按 6:2:1:1:1 分布
--    70% 落在用户偏好的两个一级类目里（保证共现信号），30% 随机探索
--    商品与类目的对应关系不变，所以相似度计算能算出有意义的结果
-- -------------------------------------------------------------
INSERT INTO `behavior` (user_id, product_id, behavior_type, weight, rating, created_at)
SELECT b.user_id,
       -- product_raw = 0 时落到 30，超过 500 时回退 30，两者都保持商品类目不变
       CASE
           WHEN b.product_raw = 0 THEN 30
           WHEN b.product_raw > 500 THEN b.product_raw - 30
           ELSE b.product_raw
           END,
       CASE
           WHEN b.type_r <= 5 THEN 'VIEW'
           WHEN b.type_r <= 7 THEN 'CLICK'
           WHEN b.type_r = 8 THEN 'CART'
           WHEN b.type_r = 9 THEN 'PURCHASE'
           ELSE 'RATE'
           END,
       CASE
           WHEN b.type_r <= 5 THEN 1.0
           WHEN b.type_r <= 7 THEN 2.0
           WHEN b.type_r = 8 THEN 3.0
           WHEN b.type_r = 9 THEN 5.0
           ELSE 4.0
           END,
       CASE WHEN b.type_r = 10 THEN 1 + MOD(b.n * 3, 5) ELSE NULL END,
       DATE_SUB(DATE_SUB(NOW(), INTERVAL (MOD(b.n * 13, 30)) DAY),
                INTERVAL (MOD(b.n * 7919, 86400)) SECOND)
FROM (
         SELECT a.n,
                a.user_id,
                a.bucket + 30 * MOD(FLOOR(a.n * 29 / 7), 17) AS product_raw,
                a.type_r
         FROM (
                  SELECT t.n,
                         t.user_id,
                         t.type_r,
                         CASE
                             WHEN MOD(t.n * 31, 100) < 70 THEN
                                 (CASE WHEN MOD(t.n, 2) = 0
                                           THEN MOD(t.user_id - 2, 10)
                                       ELSE MOD(MOD(t.user_id - 2, 10) + 3, 10)
                                     END) * 3
                                     + MOD(t.n * 7, 3)
                             ELSE MOD(t.n * 13, 30)
                             END AS bucket
                  FROM (
                           SELECT s.n,
                                  2 + MOD(s.n * 97, 200) AS user_id,
                                  MOD(s.n * 17, 11) AS type_r
                           FROM tmp_seq s
                           WHERE s.n <= 5000
                       ) t
              ) a
     ) b;

-- -------------------------------------------------------------
-- 5. 订单与明细：800 单，每单 1-3 个商品
--    先插订单（金额占位 0），再插明细，最后用明细汇总回填总金额
-- -------------------------------------------------------------
INSERT INTO `orders` (order_no, user_id, total_amount, status, receiver_name,
                      receiver_phone, receiver_addr, paid_time, create_time)
SELECT CONCAT('S', 20261028000000 + s.n),
       2 + MOD(s.n * 41, 200),
       0.00,
       1 + MOD(s.n * 7, 3),
       CONCAT('收货人', 2 + MOD(s.n * 41, 200)),
       CONCAT('1380000', LPAD(2 + MOD(s.n * 41, 200), 4, '0')),
       CONCAT('演示地址 ', 2 + MOD(s.n * 41, 200), ' 号'),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL (MOD(s.n * 11, 60)) DAY), INTERVAL 5 MINUTE),
       DATE_SUB(NOW(), INTERVAL (MOD(s.n * 11, 60)) DAY)
FROM tmp_seq s
WHERE s.n <= 800;

INSERT INTO `order_item` (order_no, product_id, product_name, price, quantity, subtotal,
                          create_time)
SELECT CONCAT('S', 20261028000000 + o.n),
       p.id,
       p.name,
       p.price,
       1 + MOD(o.n + j.n, 2),
       p.price * (1 + MOD(o.n + j.n, 2)),
       DATE_SUB(NOW(), INTERVAL (MOD(o.n * 11, 60)) DAY)
FROM tmp_seq o
         JOIN tmp_item_seq j ON j.n <= 1 + MOD(o.n * 7, 3)
         JOIN `product` p ON p.id = 1 + MOD(o.n * 23 + j.n * 37, 500)
WHERE o.n <= 800;

UPDATE `orders` o
    JOIN (SELECT order_no, SUM(subtotal) AS total FROM `order_item` GROUP BY order_no) t
ON t.order_no = o.order_no
    SET o.total_amount = t.total
-- 只回填上面写入 0.00 的占位值：既避免误改已有金额，
-- 也让这条 UPDATE 带上明确的 WHERE（无 WHERE 的 UPDATE 会被静态检查判为高危）
WHERE o.total_amount = 0.00;

-- -------------------------------------------------------------
-- 6. 评价：600 条
--    每人 3 条、每条商品不同，刚好避开 uk_user_product 唯一索引
-- -------------------------------------------------------------
INSERT INTO `review` (user_id, product_id, rating, content, create_time)
SELECT 2 + MOD(s.n - 1, 200),
       1 + MOD(MOD(s.n - 1, 200) * 7 + FLOOR((s.n - 1) / 200) * 137, 500),
       1 + MOD(s.n * 13, 5),
       CONCAT('演示评价：', s.n),
       DATE_SUB(NOW(), INTERVAL (MOD(s.n * 5, 45)) DAY)
FROM tmp_seq s
WHERE s.n <= 600;

-- -------------------------------------------------------------
-- 7. 购物车：给 user001（id=2）预置 3 条，方便直接下单演示
-- -------------------------------------------------------------
INSERT INTO `cart` (user_id, product_id, quantity, checked) VALUES
                                                                (2, 1, 1, 1),
                                                                (2, 2, 2, 1),
                                                                (2, 3, 1, 1);

DROP TEMPORARY TABLE IF EXISTS tmp_seq;
DROP TEMPORARY TABLE IF EXISTS tmp_item_seq;

-- -------------------------------------------------------------
-- 8. 导入结果自检（行数应依次为 40 / 201 / 500 / 5000 / 800 / 600 / 3）
-- -------------------------------------------------------------
SELECT 'category'   AS table_name, COUNT(*) AS rows_inserted FROM `category`
UNION ALL SELECT 'user',       COUNT(*) FROM `user`
UNION ALL SELECT 'product',    COUNT(*) FROM `product`
UNION ALL SELECT 'behavior',   COUNT(*) FROM `behavior`
UNION ALL SELECT 'orders',     COUNT(*) FROM `orders`
UNION ALL SELECT 'order_item', COUNT(*) FROM `order_item`
UNION ALL SELECT 'review',     COUNT(*) FROM `review`
UNION ALL SELECT 'cart',       COUNT(*) FROM `cart`;
