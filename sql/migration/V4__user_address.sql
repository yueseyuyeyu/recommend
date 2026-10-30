-- =============================================================
-- V4 增量迁移：用户收货地址簿
-- 适用：已按 v1/v2/v3 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V4__user_address.sql
-- =============================================================

USE recommend_db;

-- ----------------------------
-- 收货地址表
--   一个用户可以有多条地址，其中最多一条是默认地址。
--   「最多一条默认」由服务层保证：设置默认时先把该用户其他地址置 0。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user_address` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id`         BIGINT       NOT NULL                COMMENT '用户ID',
  `receiver_name`   VARCHAR(64)  NOT NULL                COMMENT '收货人',
  `receiver_phone`  VARCHAR(32)  NOT NULL                COMMENT '联系电话',
  `detail_address`  VARCHAR(255) NOT NULL                COMMENT '详细地址（含省市区）',
  `is_default`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认：1-是，0-否',
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_default` (`user_id`, `is_default`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户收货地址表';

-- ----------------------------
-- 数据迁移：把 user 表里原有的单条 address 搬到地址簿，避免老数据丢失
-- ----------------------------
INSERT INTO `user_address` (`user_id`, `receiver_name`, `receiver_phone`, `detail_address`, `is_default`)
SELECT u.`id`,
       COALESCE(NULLIF(u.`nickname`, ''), u.`username`),
       COALESCE(NULLIF(u.`phone`, ''), '未填写'),
       u.`address`,
       1
FROM `user` u
WHERE u.`address` IS NOT NULL
  AND u.`address` <> ''
  AND u.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `user_address` a WHERE a.`user_id` = u.`id`);

-- 校验
SELECT COUNT(*) AS address_rows FROM `user_address`;
