-- =============================================================
-- V12 增量迁移：补后台订单查询索引
-- 适用：已按 v1~v11 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V12__order_index.sql
--
-- 背景：
--   后台订单列表的 SQL 是「按 create_time 倒序 + 可选 status 过滤」，
--   而 orders 表原有索引只有 idx_user_status(user_id, status)。
--   后台查询不带 user_id，这个索引用不上，只能全表扫 + 文件排序。
--   订单量到十万级时这一页会明显变慢。
--
-- 两个索引的分工：
--   idx_status_create(status, create_time)  带状态筛选时用（覆盖 WHERE + ORDER BY）
--   idx_create_time(create_time)            不筛状态时用
-- 不合并成一个：status 在最左列，无法支撑「不带 status」的排序需求。
-- =============================================================

USE recommend_db;

-- 带状态筛选
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'orders'
                 AND INDEX_NAME = 'idx_status_create');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `orders` ADD INDEX `idx_status_create` (`status`, `create_time`)',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 不筛状态、只按时间倒序
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'orders'
                 AND INDEX_NAME = 'idx_create_time');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `orders` ADD INDEX `idx_create_time` (`create_time`)',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'orders'
GROUP BY INDEX_NAME;
