-- =============================================================
-- V2 增量迁移：新增收藏表与轮播图表
-- 适用：已按 schema.sql v1 初始化过的数据库
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V2__favorite_carousel.sql
--
--   不加这个参数，中文 COMMENT 会变成「鏀惰棌ID」这类乱码。
--
-- 如果已经乱码了：直接 DROP 掉这两张表再重跑本脚本即可
--   （favorite 为空表、carousel 只有本脚本内置的种子数据，重跑无损）
--   DROP TABLE IF EXISTS favorite, carousel;
-- =============================================================

USE recommend_db;

-- ----------------------------
-- 1. 商品收藏表
--    说明：收藏是轻量关系，取消收藏直接物理删除，
--          不采用其他表的 is_deleted 逻辑删除（避免表无限膨胀）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`     BIGINT   NOT NULL                COMMENT '用户ID',
  `product_id`  BIGINT   NOT NULL                COMMENT '商品ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  -- 同一用户对同一商品只能收藏一次，重复收藏由唯一键兜底
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  -- 收藏夹列表按「我的收藏时间倒序」查询
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品收藏表';

-- ----------------------------
-- 2. 首页轮播图表
--    说明：bg_color 存 CSS 渐变色值，避免依赖外部图片资源，
--          有真实图片时填 image_url 即可覆盖渐变。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `carousel` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '轮播图ID',
  `title`       VARCHAR(100) NOT NULL                COMMENT '主标题',
  `subtitle`    VARCHAR(255) DEFAULT NULL            COMMENT '副标题',
  `image_url`   VARCHAR(255) DEFAULT NULL            COMMENT '背景图URL，为空时用bg_color渐变',
  `bg_color`    VARCHAR(255) NOT NULL DEFAULT 'linear-gradient(135deg,#ff6a00,#ee0979)' COMMENT 'CSS渐变色值',
  `link_url`    VARCHAR(255) DEFAULT NULL            COMMENT '点击跳转路径，如 /products?categoryId=3',
  `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-启用，0-停用',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '首页轮播图表';

-- ----------------------------
-- 3. 轮播图种子数据（幂等：先按 title 去重）
-- ----------------------------
INSERT INTO `carousel` (`title`, `subtitle`, `bg_color`, `link_url`, `sort_order`, `status`)
SELECT * FROM (
  SELECT '个性化推荐引擎' AS title, '基于 UserCF 与 ItemCF 双引擎，为你挑选真正感兴趣的商品'
       , 'linear-gradient(135deg,#ff6a00,#ee0979)' AS bg_color, '/products' AS link_url, 1 AS sort_order, 1 AS status
  UNION ALL SELECT '协同过滤 · 猜你喜欢', '看看和你兴趣相似的人都在买什么'
       , 'linear-gradient(135deg,#1d976c,#93f9b9)', '/products', 2, 1
  UNION ALL SELECT '热门榜单', '近 30 天全站加权行为最高的商品'
       , 'linear-gradient(135deg,#2193b0,#6dd5ed)', '/products?sort=sales', 3, 1
  UNION ALL SELECT '新品上架', '刚刚加入的商品，抢先体验'
       , 'linear-gradient(135deg,#8e2de2,#4a00e0)', '/products', 4, 1
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `carousel` WHERE `title` = seed.title);
