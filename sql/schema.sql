-- =============================================================
-- 商品推荐系统 - 数据库建表脚本
-- 数据库：MySQL 8.0    库名：recommend_db
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- 引擎：InnoDB
--
-- 设计约束：
--   1. 业务数据一律逻辑删除（is_deleted），禁止物理删除
--   2. 所有表必含 create_time / update_time 审计字段
--   3. 金额统一 DECIMAL(10,2)，禁止 FLOAT/DOUBLE
--   4. 订单表命名为 orders，避开 MySQL 关键字 order
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p < sql/schema.sql
--
--   原因：Windows 下 mysql 客户端默认按 GBK 解析脚本文件，而本文件是 UTF-8。
--   不指定字符集时，表名/字段名能建对，但所有中文 COMMENT 会变成乱码，
--   典型表现是「杞?挱鍥綢D」这类字符（即 UTF-8 字节被当 GBK 解码）。
-- =============================================================

CREATE DATABASE IF NOT EXISTS recommend_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE recommend_db;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`         VARCHAR(64)  NOT NULL                COMMENT '用户名，4-20位，唯一',
  `password_hash`    VARCHAR(100) NOT NULL                COMMENT 'BCrypt加盐哈希，绝不存明文',
  `nickname`         VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
  `email`            VARCHAR(128) DEFAULT NULL            COMMENT '邮箱',
  `avatar`           VARCHAR(255) DEFAULT NULL            COMMENT '头像URL',
  `role`             TINYINT      NOT NULL DEFAULT 1      COMMENT '角色：1-普通用户，2-管理员',
  `status`           TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-正常，0-禁用',
  `last_login_time`  DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除，1-已删除',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role_status` (`role`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

-- ----------------------------
-- 2. 类目表（两级结构）
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '类目ID',
  `name`        VARCHAR(64) NOT NULL                COMMENT '类目名称',
  `parent_id`   BIGINT      NOT NULL DEFAULT 0      COMMENT '父级ID，0表示一级类目',
  `sort_order`  INT         NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
  `is_deleted`  TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品类目表';

-- ----------------------------
-- 3. 商品表
-- ----------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id`    BIGINT        NOT NULL                COMMENT '所属类目ID',
  `name`           VARCHAR(200)  NOT NULL                COMMENT '商品名称',
  `subtitle`       VARCHAR(255)  DEFAULT NULL            COMMENT '副标题',
  `main_image`     VARCHAR(255)  DEFAULT NULL            COMMENT '主图URL',
  `price`          DECIMAL(10,2) NOT NULL                COMMENT '售价',
  `original_price` DECIMAL(10,2) DEFAULT NULL            COMMENT '原价',
  `stock`          INT           NOT NULL DEFAULT 0      COMMENT '库存，不得为负',
  `sales`          INT           NOT NULL DEFAULT 0      COMMENT '累计销量',
  `status`         TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1-上架，0-下架',
  `description`    TEXT          DEFAULT NULL            COMMENT '商品详情',
  `is_deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status_sales` (`status`, `sales`),
  KEY `idx_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品表';

-- ----------------------------
-- 4. 行为表 ★推荐算法数据源
--    参考项目 newbee-mall 缺失此表，只能从订单反推，
--    导致数据稀疏、冷启动无解。本项目完整采集五类行为。
-- ----------------------------
DROP TABLE IF EXISTS `behavior`;
CREATE TABLE `behavior` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '行为ID',
  `user_id`       BIGINT       NOT NULL                COMMENT '用户ID',
  `product_id`    BIGINT       NOT NULL                COMMENT '商品ID',
  `behavior_type` VARCHAR(16)  NOT NULL                COMMENT '行为类型：VIEW/CLICK/CART/PURCHASE/RATE',
  `weight`        DECIMAL(4,2) NOT NULL                COMMENT '行为权重：1.0/2.0/3.0/5.0/4.0',
  `rating`        TINYINT      DEFAULT NULL            COMMENT '评分1-5，仅RATE类型有值',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '行为发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_user_product` (`user_id`, `product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户行为表';

-- ----------------------------
-- 5. 购物车表
-- ----------------------------
DROP TABLE IF EXISTS `cart`;
CREATE TABLE `cart` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '购物车条目ID',
  `user_id`     BIGINT   NOT NULL                COMMENT '用户ID',
  `product_id`  BIGINT   NOT NULL                COMMENT '商品ID',
  `quantity`    INT      NOT NULL DEFAULT 1      COMMENT '数量',
  `checked`     TINYINT  NOT NULL DEFAULT 1      COMMENT '是否勾选：1-是，0-否',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '购物车表';

-- ----------------------------
-- 6. 订单表（表名 orders，避开关键字 order）
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no`       VARCHAR(32)   NOT NULL                COMMENT '订单号，业务唯一',
  `user_id`        BIGINT        NOT NULL                COMMENT '用户ID',
  `total_amount`   DECIMAL(10,2) NOT NULL                COMMENT '订单总金额',
  `status`         TINYINT       NOT NULL DEFAULT 0      COMMENT '0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
  `receiver_name`  VARCHAR(64)   DEFAULT NULL            COMMENT '收货人',
  `receiver_phone` VARCHAR(32)   DEFAULT NULL            COMMENT '联系电话',
  `receiver_addr`  VARCHAR(255)  DEFAULT NULL            COMMENT '收货地址',
  `paid_time`      DATETIME      DEFAULT NULL            COMMENT '支付时间',
  `is_deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单表';

-- ----------------------------
-- 7. 订单明细表（冗余商品名与价格快照）
-- ----------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_no`     VARCHAR(32)   NOT NULL                COMMENT '订单号',
  `product_id`   BIGINT        NOT NULL                COMMENT '商品ID',
  `product_name` VARCHAR(200)  NOT NULL                COMMENT '下单时商品名快照',
  `price`        DECIMAL(10,2) NOT NULL                COMMENT '下单时单价快照',
  `quantity`     INT           NOT NULL                COMMENT '数量',
  `subtotal`     DECIMAL(10,2) NOT NULL                COMMENT '小计 = price × quantity',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单明细表';

-- ----------------------------
-- 8. 评价表
-- ----------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '用户ID',
  `product_id`  BIGINT       NOT NULL                COMMENT '商品ID',
  `order_no`    VARCHAR(32)  DEFAULT NULL            COMMENT '关联订单号',
  `rating`      TINYINT      NOT NULL                COMMENT '评分1-5',
  `content`     VARCHAR(500) DEFAULT NULL            COMMENT '评价内容',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品评价表';

-- ----------------------------
-- 9. 商品收藏表
--    收藏是轻量关系，取消收藏直接物理删除，不采用逻辑删除
-- ----------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`     BIGINT   NOT NULL                COMMENT '用户ID',
  `product_id`  BIGINT   NOT NULL                COMMENT '商品ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品收藏表';

-- ----------------------------
-- 10. 首页轮播图表
--     bg_color 存 CSS 渐变色值，避免依赖外部图片资源
-- ----------------------------
DROP TABLE IF EXISTS `carousel`;
CREATE TABLE `carousel` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '轮播图ID',
  `title`       VARCHAR(100) NOT NULL                COMMENT '主标题',
  `subtitle`    VARCHAR(255) DEFAULT NULL            COMMENT '副标题',
  `image_url`   VARCHAR(255) DEFAULT NULL            COMMENT '背景图URL，为空时用bg_color渐变',
  `bg_color`    VARCHAR(255) NOT NULL DEFAULT 'linear-gradient(135deg,#ff6a00,#ee0979)' COMMENT 'CSS渐变色值',
  `link_url`    VARCHAR(255) DEFAULT NULL            COMMENT '点击跳转路径',
  `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-启用，0-停用',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '首页轮播图表';

-- 轮播图初始数据
INSERT INTO `carousel` (`title`, `subtitle`, `bg_color`, `link_url`, `sort_order`, `status`) VALUES
('个性化推荐引擎', '基于 UserCF 与 ItemCF 双引擎，为你挑选真正感兴趣的商品', 'linear-gradient(135deg,#ff6a00,#ee0979)', '/products', 1, 1),
('协同过滤 · 猜你喜欢', '看看和你兴趣相似的人都在买什么', 'linear-gradient(135deg,#1d976c,#93f9b9)', '/products', 2, 1),
('热门榜单', '近 30 天全站加权行为最高的商品', 'linear-gradient(135deg,#2193b0,#6dd5ed)', '/products?sort=sales', 3, 1),
('新品上架', '刚刚加入的商品，抢先体验', 'linear-gradient(135deg,#8e2de2,#4a00e0)', '/products', 4, 1);
