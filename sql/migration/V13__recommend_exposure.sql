-- =============================================================
-- V13 增量迁移：推荐曝光回流
-- 适用：已按 v1~v12 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V13__recommend_exposure.sql
--
-- 背景：
--   离线评测只能回答「推荐得准不准」，回答不了「用户到底看没看、点没点」。
--   记录曝光后，配合已有的 behavior(CLICK) 就能算出真实点击率，
--   也是后续做 A/B 的前提。
--
-- 写入量提示：每次推荐流请求写入 10~20 行。学习场景完全够用；
-- 真要上量应改为异步批量落库或写入日志/消息队列。
-- =============================================================

USE recommend_db;

CREATE TABLE IF NOT EXISTS `recommend_exposure` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT      NOT NULL                COMMENT '被推荐用户',
  `product_id` BIGINT      NOT NULL                COMMENT '曝光的商品',
  `scene`      VARCHAR(32) NOT NULL                COMMENT '场景：HOME_FEED / SIMILAR / HOT',
  `position`   INT         DEFAULT NULL            COMMENT '在该场景中的位次，从 1 开始',
  `exposed_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '曝光时间',
  PRIMARY KEY (`id`),
  KEY `idx_time` (`exposed_at`),
  KEY `idx_user_time` (`user_id`, `exposed_at`),
  KEY `idx_user_product` (`user_id`, `product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '推荐曝光流水';

SELECT COUNT(*) AS exposure_rows FROM `recommend_exposure`;
