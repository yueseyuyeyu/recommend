-- =============================================================
-- V8 增量迁移：订单状态变更日志 + 站内通知
-- 适用：已按 v1~v7 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V8__order_log_notification.sql
-- =============================================================

USE recommend_db;

-- ----------------------------
-- 1. 订单状态变更日志
--    只追加不修改：每次状态流转写一行，用于回答「谁在什么时候把订单改成了什么」
--    from_status 允许为空，表示「订单创建」这一没有前置状态的事件
-- ----------------------------
CREATE TABLE IF NOT EXISTS `order_status_log` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `order_no`      VARCHAR(32) NOT NULL                COMMENT '订单号',
  `from_status`   TINYINT     DEFAULT NULL            COMMENT '变更前状态，创建时为空',
  `to_status`     TINYINT     NOT NULL                COMMENT '变更后状态',
  `operator_type` VARCHAR(16) NOT NULL                COMMENT '操作者类型：USER-用户，ADMIN-管理员，SYSTEM-系统',
  `operator_id`   BIGINT      DEFAULT NULL            COMMENT '操作者ID，系统操作时为空',
  `operator_name` VARCHAR(64) DEFAULT NULL            COMMENT '操作者名称快照',
  `remark`        VARCHAR(255) DEFAULT NULL           COMMENT '备注，如物流单号、取消原因',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单状态变更日志表';

-- ----------------------------
-- 2. 站内通知
--    发货等事件给用户推一条，前端导航栏铃铛展示未读数
-- ----------------------------
CREATE TABLE IF NOT EXISTS `notification` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '接收用户ID',
  `type`        VARCHAR(32)  NOT NULL                COMMENT '类型：ORDER_SHIPPED 等',
  `title`       VARCHAR(128) NOT NULL                COMMENT '标题',
  `content`     VARCHAR(500) DEFAULT NULL            COMMENT '正文',
  `biz_type`    VARCHAR(32)  DEFAULT NULL            COMMENT '关联业务类型，如 ORDER',
  `biz_id`      VARCHAR(64)  DEFAULT NULL            COMMENT '关联业务标识，如订单号',
  `is_read`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已读：1-是，0-否',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '站内通知表';

-- 校验
SELECT COUNT(*) AS log_rows FROM `order_status_log`;
SELECT COUNT(*) AS notification_rows FROM `notification`;
