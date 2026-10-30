-- =============================================================
-- V7 增量迁移：订单增加物流单号
-- 适用：已按 v1~v6 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V7__order_tracking_no.sql
-- =============================================================

USE recommend_db;

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后再执行
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = 'recommend_db'
                 AND TABLE_NAME = 'orders'
                 AND COLUMN_NAME = 'tracking_no');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `orders` ADD COLUMN `tracking_no` VARCHAR(64) DEFAULT NULL COMMENT ''物流单号，发货时填写'' AFTER `paid_time`',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'orders'
ORDER BY ORDINAL_POSITION;
