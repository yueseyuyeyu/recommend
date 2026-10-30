-- =============================================================
-- V9 增量迁移：订单增加发货时间与配送方
-- 适用：已按 v1~v8 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V9__order_ship_info.sql
--
-- 说明：
--   ship_time 是「发货满 7 天自动确认收货」的计时起点，
--   没有它就无从判断超时，所以本次必须补这个字段。
--   carrier 记录配送方（顺丰/邮政/中通…），便于展示与按快递公司统计。
-- =============================================================

USE recommend_db;

-- 发货时间
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = 'recommend_db'
                 AND TABLE_NAME = 'orders'
                 AND COLUMN_NAME = 'ship_time');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `orders` ADD COLUMN `ship_time` DATETIME DEFAULT NULL COMMENT ''发货时间，自动确认收货的计时起点'' AFTER `tracking_no`',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 配送方
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = 'recommend_db'
                 AND TABLE_NAME = 'orders'
                 AND COLUMN_NAME = 'carrier');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `orders` ADD COLUMN `carrier` VARCHAR(32) DEFAULT NULL COMMENT ''配送方：SF-顺丰，EMS-邮政，ZTO-中通，YTO-圆通，YUNDA-韵达，JD-京东'' AFTER `tracking_no`',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史数据回填：已发货但没有 ship_time 的订单，用最后更新时间兜底，
-- 否则它们永远不会进入自动确认收货的候选集
UPDATE `orders`
SET `ship_time` = `update_time`
WHERE `status` = 2 AND `ship_time` IS NULL AND `is_deleted` = 0;

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'orders'
ORDER BY ORDINAL_POSITION;
