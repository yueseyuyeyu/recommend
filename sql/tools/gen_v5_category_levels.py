# -*- coding: utf-8 -*-
"""生成 V5 迁移 SQL：把两级类目扩成三级，并把商品均匀挂到三级。

脚本可重复执行：
  - 二级/三级用 LEFT JOIN 判重，已存在则跳过
  - 商品重分配基于「当前所属二级」推导，无论商品当前挂在二级还是三级都成立
"""

import io
import os

OUT = r"D:\桌面\商品推荐系统\sql\migration\V5__category_three_levels.sql"

# 一级 -> (现有二级重命名 [(id, 新名)], 新增二级 [名], 三级映射 {二级名: [三级...]})
PLAN = [
    (1, "手机数码",
     [(11, "手机"), (12, "平板电脑"), (13, "耳机音响")],
     ["智能穿戴", "摄影摄像"],
     {
         "手机": ["5G手机", "折叠屏手机", "游戏手机"],
         "平板电脑": ["苹果平板", "安卓平板", "平板配件"],
         "耳机音响": ["蓝牙耳机", "头戴耳机", "音箱"],
         "智能穿戴": ["智能手表", "智能手环", "穿戴配件"],
         "摄影摄像": ["单反相机", "微单相机", "运动相机"],
     }),
    (2, "电脑办公",
     [(14, "笔记本"), (15, "台式机"), (16, "显示器")],
     ["办公设备", "电脑配件"],
     {
         "笔记本": ["轻薄本", "游戏本", "商务本"],
         "台式机": ["品牌整机", "组装机", "一体机"],
         "显示器": ["电竞显示器", "办公显示器", "便携显示器"],
         "办公设备": ["打印机", "扫描仪", "投影仪"],
         "电脑配件": ["键盘鼠标", "内存硬盘", "机箱电源"],
     }),
    (3, "家用电器",
     [(17, "电视"), (18, "空调"), (19, "冰箱洗衣机")],
     ["厨房电器", "生活电器"],
     {
         "电视": ["4K电视", "智能电视", "电视配件"],
         "空调": ["挂式空调", "柜式空调", "中央空调"],
         "冰箱洗衣机": ["冰箱", "洗衣机", "洗烘一体"],
         "厨房电器": ["电饭煲", "微波炉", "空气炸锅"],
         "生活电器": ["吸尘器", "加湿器", "电风扇"],
     }),
    (4, "服饰鞋包",
     [(20, "女装"), (21, "男装"), (22, "女鞋")],
     ["男鞋", "箱包"],
     {
         "女装": ["连衣裙", "女式上衣", "女裤"],
         "男装": ["男式T恤", "衬衫", "外套"],
         "女鞋": ["高跟鞋", "单鞋", "靴子"],
         "男鞋": ["运动鞋", "皮鞋", "休闲鞋"],
         "箱包": ["双肩包", "手提包", "行李箱"],
     }),
    (5, "美妆个护",
     [(23, "面部护肤"), (24, "彩妆"), (25, "香水")],
     ["洗发护发", "口腔护理"],
     {
         "面部护肤": ["洁面", "面霜", "精华"],
         "彩妆": ["口红", "粉底", "眼影"],
         "香水": ["女士香水", "男士香水", "香氛"],
         "洗发护发": ["洗发水", "护发素", "沐浴露"],
         "口腔护理": ["牙膏", "牙刷", "漱口水"],
     }),
    (6, "食品生鲜",
     [(26, "休闲零食"), (27, "粮油调味"), (28, "生鲜果蔬")],
     ["酒水饮料", "乳品烘焙"],
     {
         "休闲零食": ["坚果炒货", "饼干糕点", "肉干肉脯"],
         "粮油调味": ["大米", "食用油", "调味品"],
         "生鲜果蔬": ["新鲜水果", "时令蔬菜", "海鲜水产"],
         "酒水饮料": ["白酒", "葡萄酒", "饮料"],
         "乳品烘焙": ["牛奶", "酸奶", "面包"],
     }),
    (7, "运动户外",
     [(29, "运动服饰"), (30, "运动鞋"), (31, "健身器材")],
     ["户外装备", "游泳装备"],
     {
         "运动服饰": ["运动上衣", "运动裤", "运动外套"],
         "运动鞋": ["跑步鞋", "篮球鞋", "足球鞋"],
         "健身器材": ["哑铃", "跑步机", "瑜伽垫"],
         "户外装备": ["帐篷", "登山包", "户外照明"],
         "游泳装备": ["泳衣", "泳镜", "泳帽"],
     }),
    (8, "图书文娱",
     [(32, "小说文学"), (33, "教辅考试"), (34, "童书绘本")],
     ["文具", "乐器"],
     {
         "小说文学": ["当代小说", "外国文学", "散文随笔"],
         "教辅考试": ["考研用书", "公务员", "英语学习"],
         "童书绘本": ["绘本", "科普读物", "启蒙认知"],
         "文具": ["笔记本", "笔类", "办公文具"],
         "乐器": ["吉他", "键盘乐器", "打击乐器"],
     }),
    (9, "家居日用",
     [(35, "家具"), (36, "家纺"), (37, "厨具")],
     ["收纳清洁", "灯具"],
     {
         "家具": ["沙发", "床垫床架", "桌椅"],
         "家纺": ["床品套件", "毛巾浴巾", "窗帘"],
         "厨具": ["锅具", "刀具", "餐具"],
         "收纳清洁": ["收纳箱", "清洁用品", "垃圾袋"],
         "灯具": ["吸顶灯", "台灯", "氛围灯"],
     }),
    (10, "母婴玩具",
     [(38, "奶粉辅食"), (39, "纸尿裤"), (40, "童装童鞋")],
     ["玩具", "婴儿推车"],
     {
         "奶粉辅食": ["婴儿奶粉", "米粉", "果泥"],
         "纸尿裤": ["拉拉裤", "婴儿湿巾", "纸尿片"],
         "童装童鞋": ["童装", "童鞋", "亲子装"],
         "玩具": ["积木", "毛绒玩具", "益智玩具"],
         "婴儿推车": ["婴儿推车", "安全座椅", "婴儿背带"],
     }),
]

lines = []
add = lines.append

add("-- =============================================================")
add("-- V5 增量迁移：两级类目扩为三级，并把商品均匀挂到三级")
add("-- 适用：已按 v1~v4 初始化、类目表仍是 seed.sql 原始结构")
add("--")
add("-- ⚠️ 执行方式（必须带 --default-character-set=utf8mb4）")
add("--   mysql --default-character-set=utf8mb4 -u root -p recommend_db \\")
add("--     < sql/migration/V5__category_three_levels.sql")
add("--")
add("-- 本脚本可重复执行（幂等）：")
add("--   * 二级/三级插入前用 LEFT JOIN 判重，已存在则跳过")
add("--   * 商品重分配基于「商品当前所属二级」推导，")
add("--     无论商品此刻挂在二级还是三级，再次执行都能得到同样的均分结果")
add("-- =============================================================")
add("")
add("USE recommend_db;")
add("")
add("-- ---------- 1. 二级改名（按 id 精确改，重复执行结果不变）----------")
for _top_id, _top_name, renames, _adds, _subs in PLAN:
    for cid, new_name in renames:
        add(f"UPDATE `category` SET `name` = '{new_name}' WHERE `id` = {cid};")
add("")
add("-- ---------- 2. 新增二级（每个一级补齐到 5 个）----------")
for top_id, _top_name, _renames, adds, _subs in PLAN:
    for idx, name in enumerate(adds, start=4):
        add("INSERT INTO `category` (`name`, `parent_id`, `sort_order`)")
        add(f"SELECT '{name}', p.`id`, {idx}")
        add("FROM `category` p")
        add(f"LEFT JOIN `category` e ON e.`parent_id` = p.`id` AND e.`name` = '{name}'")
        add(f"WHERE p.`id` = {top_id} AND p.`parent_id` = 0 AND e.`id` IS NULL;")
add("")
add("-- ---------- 3. 三级（每个二级挂 3 个）----------")
for top_id, top_name, _renames, _adds, subs in PLAN:
    add(f"-- {top_name}")
    for sub_name, thirds in subs.items():
        for idx, name in enumerate(thirds, start=1):
            add("INSERT INTO `category` (`name`, `parent_id`, `sort_order`)")
            add(f"SELECT '{name}', c.`id`, {idx}")
            add("FROM `category` c")
            add(f"LEFT JOIN `category` e ON e.`parent_id` = c.`id` AND e.`name` = '{name}'")
            add(f"WHERE c.`name` = '{sub_name}' AND c.`parent_id` = {top_id} AND e.`id` IS NULL;")
add("")
add("-- ---------- 4. 商品均匀挂到三级 ----------")
add("-- 分配依据是「商品在其所属二级内的序号」，不是商品 id 取模。")
add("-- 原因：种子数据里商品 id 与类目强相关（同一二级的商品 id 相差 30），")
add("-- 直接 MOD(id, 3) 在同一二级内结果恒定，会把整组商品堆进同一个三级。")
add("-- 组内序号则必然在所有三级之间轮转。")
add("--")
add("-- 两段式：先把「商品 × 可选三级」全集连同 rn/want 落进临时表，")
add("-- 再在 UPDATE 的 JOIN 条件里筛 rn = want，避免谓词被下推到窗口函数之上。")
add("DROP TEMPORARY TABLE IF EXISTS `tmp_rank`;")
add("CREATE TEMPORARY TABLE `tmp_rank` (")
add("  `pid`    BIGINT NOT NULL,")
add("  `new_id` BIGINT NOT NULL,")
add("  `rn`     INT    NOT NULL,")
add("  `want`   INT    NOT NULL,")
add("  KEY `idx_pid` (`pid`)")
add(") ENGINE = MEMORY;")
add("")
add("INSERT INTO `tmp_rank` (`pid`, `new_id`, `rn`, `want`)")
add("SELECT r.`pid`,")
add("       x.`id`,")
add("       x.`rn`,")
add("       MOD(r.`prn` - 1, x.`cnt`) + 1")
add("FROM (")
add("    SELECT p.`id` AS pid,")
add("           lv2.`id` AS lv2_id,")
add("           ROW_NUMBER() OVER (PARTITION BY lv2.`id` ORDER BY p.`id`) AS prn")
add("    FROM `product` p")
add("    -- cur = 商品当前所属类目")
add("    JOIN `category` cur ON cur.`id` = p.`category_id`")
add("    JOIN `category` par ON par.`id` = cur.`parent_id`")
add("    -- 定位「二级」：cur 是二级时取 cur 自己；cur 是三级时取它的父")
add("    JOIN `category` lv2 ON lv2.`id` = IF(par.`parent_id` = 0, cur.`id`, cur.`parent_id`)")
add(") r")
add("-- 该二级下所有三级，按 id 编号")
add("JOIN (")
add("    SELECT `id`, `parent_id`,")
add("           ROW_NUMBER() OVER (PARTITION BY `parent_id` ORDER BY `id`) AS rn,")
add("           COUNT(*)      OVER (PARTITION BY `parent_id`)             AS cnt")
add("    FROM `category`")
add("    WHERE `parent_id` > 0")
add(") x ON x.`parent_id` = r.`lv2_id`;")
add("")
add("UPDATE `product` p")
add("JOIN `tmp_rank` r ON r.`pid` = p.`id` AND r.`rn` = r.`want`")
add("SET p.`category_id` = r.`new_id`;")
add("")
add("DROP TEMPORARY TABLE IF EXISTS `tmp_rank`;")
add("")
add("-- ---------- 校验 ----------")
add("SELECT")
add("  SUM(CASE WHEN `parent_id` = 0 THEN 1 ELSE 0 END) AS lv1,")
add("  SUM(CASE WHEN `parent_id` BETWEEN 1 AND 10 THEN 1 ELSE 0 END) AS lv2,")
add("  SUM(CASE WHEN `parent_id` > 10 THEN 1 ELSE 0 END) AS lv3")
add("FROM `category`;")
add("")
add("SELECT MIN(c) AS min_per_lv3, MAX(c) AS max_per_lv3 FROM (")
add("  SELECT COUNT(p.`id`) AS c")
add("  FROM `category` c3")
add("  LEFT JOIN `product` p ON p.`category_id` = c3.`id`")
add("  WHERE c3.`parent_id` > 10")
add("  GROUP BY c3.`id`")
add(") t;")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines) + "\n")

print(f"已生成 {OUT}，共 {len(lines)} 行")
