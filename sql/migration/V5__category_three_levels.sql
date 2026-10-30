-- =============================================================
-- V5 增量迁移：两级类目扩为三级，并把商品均匀挂到三级
-- 适用：已按 v1~v4 初始化、类目表仍是 seed.sql 原始结构
--
-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）
--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \
--     < sql/migration/V5__category_three_levels.sql
--
-- 本脚本可重复执行（幂等）：
--   * 二级/三级插入前用 LEFT JOIN 判重，已存在则跳过
--   * 商品重分配基于「商品当前所属二级」推导，
--     无论商品此刻挂在二级还是三级，再次执行都能得到同样的均分结果
-- =============================================================

USE recommend_db;

-- ---------- 1. 二级改名（按 id 精确改，重复执行结果不变）----------
UPDATE `category` SET `name` = '手机' WHERE `id` = 11;
UPDATE `category` SET `name` = '平板电脑' WHERE `id` = 12;
UPDATE `category` SET `name` = '耳机音响' WHERE `id` = 13;
UPDATE `category` SET `name` = '笔记本' WHERE `id` = 14;
UPDATE `category` SET `name` = '台式机' WHERE `id` = 15;
UPDATE `category` SET `name` = '显示器' WHERE `id` = 16;
UPDATE `category` SET `name` = '电视' WHERE `id` = 17;
UPDATE `category` SET `name` = '空调' WHERE `id` = 18;
UPDATE `category` SET `name` = '冰箱洗衣机' WHERE `id` = 19;
UPDATE `category` SET `name` = '女装' WHERE `id` = 20;
UPDATE `category` SET `name` = '男装' WHERE `id` = 21;
UPDATE `category` SET `name` = '女鞋' WHERE `id` = 22;
UPDATE `category` SET `name` = '面部护肤' WHERE `id` = 23;
UPDATE `category` SET `name` = '彩妆' WHERE `id` = 24;
UPDATE `category` SET `name` = '香水' WHERE `id` = 25;
UPDATE `category` SET `name` = '休闲零食' WHERE `id` = 26;
UPDATE `category` SET `name` = '粮油调味' WHERE `id` = 27;
UPDATE `category` SET `name` = '生鲜果蔬' WHERE `id` = 28;
UPDATE `category` SET `name` = '运动服饰' WHERE `id` = 29;
UPDATE `category` SET `name` = '运动鞋' WHERE `id` = 30;
UPDATE `category` SET `name` = '健身器材' WHERE `id` = 31;
UPDATE `category` SET `name` = '小说文学' WHERE `id` = 32;
UPDATE `category` SET `name` = '教辅考试' WHERE `id` = 33;
UPDATE `category` SET `name` = '童书绘本' WHERE `id` = 34;
UPDATE `category` SET `name` = '家具' WHERE `id` = 35;
UPDATE `category` SET `name` = '家纺' WHERE `id` = 36;
UPDATE `category` SET `name` = '厨具' WHERE `id` = 37;
UPDATE `category` SET `name` = '奶粉辅食' WHERE `id` = 38;
UPDATE `category` SET `name` = '纸尿裤' WHERE `id` = 39;
UPDATE `category` SET `name` = '童装童鞋' WHERE `id` = 40;

-- ---------- 2. 新增二级（每个一级补齐到 5 个）----------
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '智能穿戴', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '智能穿戴'
WHERE p.`id` = 1 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '摄影摄像', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '摄影摄像'
WHERE p.`id` = 1 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '办公设备', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '办公设备'
WHERE p.`id` = 2 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '电脑配件', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '电脑配件'
WHERE p.`id` = 2 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '厨房电器', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '厨房电器'
WHERE p.`id` = 3 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '生活电器', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '生活电器'
WHERE p.`id` = 3 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '男鞋', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '男鞋'
WHERE p.`id` = 4 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '箱包', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '箱包'
WHERE p.`id` = 4 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '洗发护发', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '洗发护发'
WHERE p.`id` = 5 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '口腔护理', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '口腔护理'
WHERE p.`id` = 5 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '酒水饮料', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '酒水饮料'
WHERE p.`id` = 6 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '乳品烘焙', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '乳品烘焙'
WHERE p.`id` = 6 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '户外装备', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '户外装备'
WHERE p.`id` = 7 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '游泳装备', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '游泳装备'
WHERE p.`id` = 7 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '文具', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '文具'
WHERE p.`id` = 8 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '乐器', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '乐器'
WHERE p.`id` = 8 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '收纳清洁', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '收纳清洁'
WHERE p.`id` = 9 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '灯具', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '灯具'
WHERE p.`id` = 9 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '玩具', p.`id`, 4
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '玩具'
WHERE p.`id` = 10 AND p.`parent_id` = 0 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '婴儿推车', p.`id`, 5
FROM `category` p
LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '婴儿推车'
WHERE p.`id` = 10 AND p.`parent_id` = 0 AND e.`id` IS NULL;

-- ---------- 3. 三级（每个二级挂 3 个）----------
-- 手机数码
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '5G手机', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '5G手机'
WHERE c.`name` = '手机' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '折叠屏手机', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '折叠屏手机'
WHERE c.`name` = '手机' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '游戏手机', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '游戏手机'
WHERE c.`name` = '手机' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '苹果平板', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '苹果平板'
WHERE c.`name` = '平板电脑' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '安卓平板', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '安卓平板'
WHERE c.`name` = '平板电脑' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '平板配件', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '平板配件'
WHERE c.`name` = '平板电脑' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '蓝牙耳机', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '蓝牙耳机'
WHERE c.`name` = '耳机音响' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '头戴耳机', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '头戴耳机'
WHERE c.`name` = '耳机音响' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '音箱', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '音箱'
WHERE c.`name` = '耳机音响' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '智能手表', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '智能手表'
WHERE c.`name` = '智能穿戴' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '智能手环', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '智能手环'
WHERE c.`name` = '智能穿戴' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '穿戴配件', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '穿戴配件'
WHERE c.`name` = '智能穿戴' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '单反相机', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '单反相机'
WHERE c.`name` = '摄影摄像' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '微单相机', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '微单相机'
WHERE c.`name` = '摄影摄像' AND c.`parent_id` = 1 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '运动相机', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '运动相机'
WHERE c.`name` = '摄影摄像' AND c.`parent_id` = 1 AND e.`id` IS NULL;
-- 电脑办公
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '轻薄本', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '轻薄本'
WHERE c.`name` = '笔记本' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '游戏本', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '游戏本'
WHERE c.`name` = '笔记本' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '商务本', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '商务本'
WHERE c.`name` = '笔记本' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '品牌整机', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '品牌整机'
WHERE c.`name` = '台式机' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '组装机', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '组装机'
WHERE c.`name` = '台式机' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '一体机', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '一体机'
WHERE c.`name` = '台式机' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '电竞显示器', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '电竞显示器'
WHERE c.`name` = '显示器' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '办公显示器', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '办公显示器'
WHERE c.`name` = '显示器' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '便携显示器', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '便携显示器'
WHERE c.`name` = '显示器' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '打印机', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '打印机'
WHERE c.`name` = '办公设备' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '扫描仪', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '扫描仪'
WHERE c.`name` = '办公设备' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '投影仪', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '投影仪'
WHERE c.`name` = '办公设备' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '键盘鼠标', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '键盘鼠标'
WHERE c.`name` = '电脑配件' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '内存硬盘', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '内存硬盘'
WHERE c.`name` = '电脑配件' AND c.`parent_id` = 2 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '机箱电源', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '机箱电源'
WHERE c.`name` = '电脑配件' AND c.`parent_id` = 2 AND e.`id` IS NULL;
-- 家用电器
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '4K电视', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '4K电视'
WHERE c.`name` = '电视' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '智能电视', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '智能电视'
WHERE c.`name` = '电视' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '电视配件', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '电视配件'
WHERE c.`name` = '电视' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '挂式空调', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '挂式空调'
WHERE c.`name` = '空调' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '柜式空调', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '柜式空调'
WHERE c.`name` = '空调' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '中央空调', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '中央空调'
WHERE c.`name` = '空调' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '冰箱', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '冰箱'
WHERE c.`name` = '冰箱洗衣机' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '洗衣机', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '洗衣机'
WHERE c.`name` = '冰箱洗衣机' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '洗烘一体', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '洗烘一体'
WHERE c.`name` = '冰箱洗衣机' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '电饭煲', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '电饭煲'
WHERE c.`name` = '厨房电器' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '微波炉', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '微波炉'
WHERE c.`name` = '厨房电器' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '空气炸锅', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '空气炸锅'
WHERE c.`name` = '厨房电器' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '吸尘器', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '吸尘器'
WHERE c.`name` = '生活电器' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '加湿器', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '加湿器'
WHERE c.`name` = '生活电器' AND c.`parent_id` = 3 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '电风扇', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '电风扇'
WHERE c.`name` = '生活电器' AND c.`parent_id` = 3 AND e.`id` IS NULL;
-- 服饰鞋包
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '连衣裙', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '连衣裙'
WHERE c.`name` = '女装' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '女式上衣', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '女式上衣'
WHERE c.`name` = '女装' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '女裤', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '女裤'
WHERE c.`name` = '女装' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '男式T恤', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '男式T恤'
WHERE c.`name` = '男装' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '衬衫', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '衬衫'
WHERE c.`name` = '男装' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '外套', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '外套'
WHERE c.`name` = '男装' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '高跟鞋', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '高跟鞋'
WHERE c.`name` = '女鞋' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '单鞋', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '单鞋'
WHERE c.`name` = '女鞋' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '靴子', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '靴子'
WHERE c.`name` = '女鞋' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '运动鞋', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '运动鞋'
WHERE c.`name` = '男鞋' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '皮鞋', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '皮鞋'
WHERE c.`name` = '男鞋' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '休闲鞋', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '休闲鞋'
WHERE c.`name` = '男鞋' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '双肩包', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '双肩包'
WHERE c.`name` = '箱包' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '手提包', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '手提包'
WHERE c.`name` = '箱包' AND c.`parent_id` = 4 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '行李箱', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '行李箱'
WHERE c.`name` = '箱包' AND c.`parent_id` = 4 AND e.`id` IS NULL;
-- 美妆个护
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '洁面', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '洁面'
WHERE c.`name` = '面部护肤' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '面霜', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '面霜'
WHERE c.`name` = '面部护肤' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '精华', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '精华'
WHERE c.`name` = '面部护肤' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '口红', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '口红'
WHERE c.`name` = '彩妆' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '粉底', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '粉底'
WHERE c.`name` = '彩妆' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '眼影', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '眼影'
WHERE c.`name` = '彩妆' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '女士香水', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '女士香水'
WHERE c.`name` = '香水' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '男士香水', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '男士香水'
WHERE c.`name` = '香水' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '香氛', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '香氛'
WHERE c.`name` = '香水' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '洗发水', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '洗发水'
WHERE c.`name` = '洗发护发' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '护发素', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '护发素'
WHERE c.`name` = '洗发护发' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '沐浴露', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '沐浴露'
WHERE c.`name` = '洗发护发' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '牙膏', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '牙膏'
WHERE c.`name` = '口腔护理' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '牙刷', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '牙刷'
WHERE c.`name` = '口腔护理' AND c.`parent_id` = 5 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '漱口水', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '漱口水'
WHERE c.`name` = '口腔护理' AND c.`parent_id` = 5 AND e.`id` IS NULL;
-- 食品生鲜
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '坚果炒货', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '坚果炒货'
WHERE c.`name` = '休闲零食' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '饼干糕点', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '饼干糕点'
WHERE c.`name` = '休闲零食' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '肉干肉脯', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '肉干肉脯'
WHERE c.`name` = '休闲零食' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '大米', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '大米'
WHERE c.`name` = '粮油调味' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '食用油', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '食用油'
WHERE c.`name` = '粮油调味' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '调味品', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '调味品'
WHERE c.`name` = '粮油调味' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '新鲜水果', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '新鲜水果'
WHERE c.`name` = '生鲜果蔬' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '时令蔬菜', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '时令蔬菜'
WHERE c.`name` = '生鲜果蔬' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '海鲜水产', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '海鲜水产'
WHERE c.`name` = '生鲜果蔬' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '白酒', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '白酒'
WHERE c.`name` = '酒水饮料' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '葡萄酒', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '葡萄酒'
WHERE c.`name` = '酒水饮料' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '饮料', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '饮料'
WHERE c.`name` = '酒水饮料' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '牛奶', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '牛奶'
WHERE c.`name` = '乳品烘焙' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '酸奶', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '酸奶'
WHERE c.`name` = '乳品烘焙' AND c.`parent_id` = 6 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '面包', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '面包'
WHERE c.`name` = '乳品烘焙' AND c.`parent_id` = 6 AND e.`id` IS NULL;
-- 运动户外
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '运动上衣', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '运动上衣'
WHERE c.`name` = '运动服饰' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '运动裤', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '运动裤'
WHERE c.`name` = '运动服饰' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '运动外套', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '运动外套'
WHERE c.`name` = '运动服饰' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '跑步鞋', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '跑步鞋'
WHERE c.`name` = '运动鞋' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '篮球鞋', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '篮球鞋'
WHERE c.`name` = '运动鞋' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '足球鞋', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '足球鞋'
WHERE c.`name` = '运动鞋' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '哑铃', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '哑铃'
WHERE c.`name` = '健身器材' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '跑步机', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '跑步机'
WHERE c.`name` = '健身器材' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '瑜伽垫', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '瑜伽垫'
WHERE c.`name` = '健身器材' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '帐篷', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '帐篷'
WHERE c.`name` = '户外装备' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '登山包', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '登山包'
WHERE c.`name` = '户外装备' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '户外照明', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '户外照明'
WHERE c.`name` = '户外装备' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '泳衣', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '泳衣'
WHERE c.`name` = '游泳装备' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '泳镜', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '泳镜'
WHERE c.`name` = '游泳装备' AND c.`parent_id` = 7 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '泳帽', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '泳帽'
WHERE c.`name` = '游泳装备' AND c.`parent_id` = 7 AND e.`id` IS NULL;
-- 图书文娱
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '当代小说', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '当代小说'
WHERE c.`name` = '小说文学' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '外国文学', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '外国文学'
WHERE c.`name` = '小说文学' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '散文随笔', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '散文随笔'
WHERE c.`name` = '小说文学' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '考研用书', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '考研用书'
WHERE c.`name` = '教辅考试' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '公务员', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '公务员'
WHERE c.`name` = '教辅考试' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '英语学习', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '英语学习'
WHERE c.`name` = '教辅考试' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '绘本', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '绘本'
WHERE c.`name` = '童书绘本' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '科普读物', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '科普读物'
WHERE c.`name` = '童书绘本' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '启蒙认知', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '启蒙认知'
WHERE c.`name` = '童书绘本' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '笔记本', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '笔记本'
WHERE c.`name` = '文具' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '笔类', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '笔类'
WHERE c.`name` = '文具' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '办公文具', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '办公文具'
WHERE c.`name` = '文具' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '吉他', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '吉他'
WHERE c.`name` = '乐器' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '键盘乐器', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '键盘乐器'
WHERE c.`name` = '乐器' AND c.`parent_id` = 8 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '打击乐器', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '打击乐器'
WHERE c.`name` = '乐器' AND c.`parent_id` = 8 AND e.`id` IS NULL;
-- 家居日用
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '沙发', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '沙发'
WHERE c.`name` = '家具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '床垫床架', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '床垫床架'
WHERE c.`name` = '家具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '桌椅', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '桌椅'
WHERE c.`name` = '家具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '床品套件', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '床品套件'
WHERE c.`name` = '家纺' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '毛巾浴巾', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '毛巾浴巾'
WHERE c.`name` = '家纺' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '窗帘', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '窗帘'
WHERE c.`name` = '家纺' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '锅具', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '锅具'
WHERE c.`name` = '厨具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '刀具', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '刀具'
WHERE c.`name` = '厨具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '餐具', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '餐具'
WHERE c.`name` = '厨具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '收纳箱', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '收纳箱'
WHERE c.`name` = '收纳清洁' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '清洁用品', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '清洁用品'
WHERE c.`name` = '收纳清洁' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '垃圾袋', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '垃圾袋'
WHERE c.`name` = '收纳清洁' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '吸顶灯', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '吸顶灯'
WHERE c.`name` = '灯具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '台灯', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '台灯'
WHERE c.`name` = '灯具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '氛围灯', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '氛围灯'
WHERE c.`name` = '灯具' AND c.`parent_id` = 9 AND e.`id` IS NULL;
-- 母婴玩具
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '婴儿奶粉', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '婴儿奶粉'
WHERE c.`name` = '奶粉辅食' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '米粉', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '米粉'
WHERE c.`name` = '奶粉辅食' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '果泥', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '果泥'
WHERE c.`name` = '奶粉辅食' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '拉拉裤', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '拉拉裤'
WHERE c.`name` = '纸尿裤' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '婴儿湿巾', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '婴儿湿巾'
WHERE c.`name` = '纸尿裤' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '纸尿片', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '纸尿片'
WHERE c.`name` = '纸尿裤' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '童装', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '童装'
WHERE c.`name` = '童装童鞋' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '童鞋', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '童鞋'
WHERE c.`name` = '童装童鞋' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '亲子装', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '亲子装'
WHERE c.`name` = '童装童鞋' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '积木', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '积木'
WHERE c.`name` = '玩具' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '毛绒玩具', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '毛绒玩具'
WHERE c.`name` = '玩具' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '益智玩具', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '益智玩具'
WHERE c.`name` = '玩具' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '婴儿推车', c.`id`, 1
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '婴儿推车'
WHERE c.`name` = '婴儿推车' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '安全座椅', c.`id`, 2
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '安全座椅'
WHERE c.`name` = '婴儿推车' AND c.`parent_id` = 10 AND e.`id` IS NULL;
INSERT INTO `category` (`name`, `parent_id`, `sort_order`)
SELECT '婴儿背带', c.`id`, 3
FROM `category` c
LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '婴儿背带'
WHERE c.`name` = '婴儿推车' AND c.`parent_id` = 10 AND e.`id` IS NULL;

-- ---------- 4. 商品均匀挂到三级 ----------
-- 分配依据是「商品在其所属二级内的序号」，不是商品 id 取模。
-- 原因：种子数据里商品 id 与类目强相关（同一二级的商品 id 相差 30），
-- 直接 MOD(id, 3) 在同一二级内结果恒定，会把整组商品堆进同一个三级。
-- 组内序号则必然在所有三级之间轮转。
--
-- 两段式：先把「商品 × 可选三级」全集连同 rn/want 落进临时表，
-- 再在 UPDATE 的 JOIN 条件里筛 rn = want，避免谓词被下推到窗口函数之上。
DROP TEMPORARY TABLE IF EXISTS `tmp_rank`;
CREATE TEMPORARY TABLE `tmp_rank` (
  `pid`    BIGINT NOT NULL,
  `new_id` BIGINT NOT NULL,
  `rn`     INT    NOT NULL,
  `want`   INT    NOT NULL,
  KEY `idx_pid` (`pid`)
) ENGINE = MEMORY;

INSERT INTO `tmp_rank` (`pid`, `new_id`, `rn`, `want`)
SELECT r.`pid`,
       x.`id`,
       x.`rn`,
       MOD(r.`prn` - 1, x.`cnt`) + 1
FROM (
    SELECT p.`id` AS pid,
           lv2.`id` AS lv2_id,
           ROW_NUMBER() OVER (PARTITION BY lv2.`id` ORDER BY p.`id`) AS prn
    FROM `product` p
    -- cur = 商品当前所属类目
    JOIN `category` cur ON cur.`id` = p.`category_id`
    JOIN `category` par ON par.`id` = cur.`parent_id`
    -- 定位「二级」：cur 是二级时取 cur 自己；cur 是三级时取它的父
    JOIN `category` lv2 ON lv2.`id` = IF(par.`parent_id` = 0, cur.`id`, cur.`parent_id`)
) r
-- 该二级下所有三级，按 id 编号
JOIN (
    SELECT `id`, `parent_id`,
           ROW_NUMBER() OVER (PARTITION BY `parent_id` ORDER BY `id`) AS rn,
           COUNT(*)      OVER (PARTITION BY `parent_id`)             AS cnt
    FROM `category`
    WHERE `parent_id` > 0
) x ON x.`parent_id` = r.`lv2_id`;

UPDATE `product` p
JOIN `tmp_rank` r ON r.`pid` = p.`id` AND r.`rn` = r.`want`
SET p.`category_id` = r.`new_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_rank`;

-- ---------- 校验 ----------
SELECT
  SUM(CASE WHEN `parent_id` = 0 THEN 1 ELSE 0 END) AS lv1,
  SUM(CASE WHEN `parent_id` BETWEEN 1 AND 10 THEN 1 ELSE 0 END) AS lv2,
  SUM(CASE WHEN `parent_id` > 10 THEN 1 ELSE 0 END) AS lv3
FROM `category`;

SELECT MIN(c) AS min_per_lv3, MAX(c) AS max_per_lv3 FROM (
  SELECT COUNT(p.`id`) AS c
  FROM `category` c3
  LEFT JOIN `product` p ON p.`category_id` = c3.`id`
  WHERE c3.`parent_id` > 10
  GROUP BY c3.`id`
) t;
