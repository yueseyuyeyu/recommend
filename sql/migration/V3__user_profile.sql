-- =============================================================
-- V3 增量迁移：用户表补充手机号与收货地址
-- 适用：已按 schema.sql v1/v2 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V3__user_profile.sql
--
--   不加这个参数，新增字段的中文 COMMENT 会变成乱码。
--
-- 说明：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
--       因此用 information_schema 判断后再动态执行，保证可重复运行。
-- =============================================================

USE recommend_db;

-- 手机号
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = 'recommend_db'
                 AND TABLE_NAME = 'user'
                 AND COLUMN_NAME = 'phone');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(32) DEFAULT NULL COMMENT ''手机号'' AFTER `email`',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 默认收货地址
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = 'recommend_db'
                 AND TABLE_NAME = 'user'
                 AND COLUMN_NAME = 'address');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `user` ADD COLUMN `address` VARCHAR(255) DEFAULT NULL COMMENT ''默认收货地址'' AFTER `phone`',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'user'
ORDER BY ORDINAL_POSITION;
