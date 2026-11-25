-- =============================================================
-- V14 增量迁移：商品名称全文索引（中文 ngram）
-- 适用：已按 v1~v13 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V14__product_fulltext.sql
--
-- 背景：
--   搜索原先用 LIKE '%关键词%'，前导通配符让 idx_name 失效，
--   商品上万后每次搜索都是全表扫。
--
-- 为什么用 ngram：
--   MySQL 默认分词器按空格切词，对中文整句无效；ngram 按固定长度切分
--   （@@ngram_token_size 默认 2），「手机」这类两字词能直接命中。
--   代价是单个汉字（长度 < 2）无法匹配，服务层对此回退 LIKE 兜底。
-- =============================================================

USE recommend_db;

SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'product'
                 AND INDEX_NAME = 'ft_product_name');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `product` ADD FULLTEXT INDEX `ft_product_name` (`name`, `subtitle`) WITH PARSER ngram',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT INDEX_NAME, INDEX_TYPE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'recommend_db' AND TABLE_NAME = 'product'
GROUP BY INDEX_NAME, INDEX_TYPE;
