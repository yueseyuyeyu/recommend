-- =============================================================
-- V11 增量迁移：令牌黑名单（服务端注销）
-- 适用：已按 v1~v10 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V11__revoked_token.sql
--
-- 说明：
--   JWT 无状态的代价是「签出去就收不回」。注销时把令牌的 SHA-256 摘要记下来，
--   鉴权过滤器命中黑名单即视为未登录。
--   只存摘要不存明文：数据库泄露也无法据此伪造令牌。
-- =============================================================

USE recommend_db;

CREATE TABLE IF NOT EXISTS `revoked_token` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `token_hash`  CHAR(64)    NOT NULL                COMMENT '令牌 SHA-256 摘要（十六进制）',
  `user_id`     BIGINT      NOT NULL                COMMENT '所属用户',
  `expire_at`   DATETIME    NOT NULL                COMMENT '令牌自身过期时间，过期后本行可清理',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注销时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_expire_at` (`expire_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '已注销令牌黑名单';

SELECT COUNT(*) AS revoked_rows FROM `revoked_token`;
