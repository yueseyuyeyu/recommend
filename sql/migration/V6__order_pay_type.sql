-- =============================================================
-- V6 增量迁移：订单增加支付方式
-- 适用：已按 v1~v5 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V6__order_pay_type.sql
-- =============================================================

USE recommend_db;

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后再执行
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = 'recommend_db'
                 AND TABLE_NAME = 'orders'
                 AND COLUMN_NAME = 'pay_type');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `orders` ADD COLUMN `pay_type` TINYINT DEFAULT NULL COMMENT ''支付方式：1-支付宝，2-微信支付，3-银行卡'' AFTER `status`',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'orders'
ORDER BY ORDINAL_POSITION;
