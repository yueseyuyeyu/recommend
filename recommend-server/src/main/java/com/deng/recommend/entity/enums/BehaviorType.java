package com.deng.recommend.entity.enums;

import lombok.Getter;

/**
 * 用户行为类型枚举，定义各行为对兴趣度量的贡献权重。
 *
 * <p>权重设计依据：显式程度越高、越接近最终转化的行为，权重越大。
 * 购买(5.0) &gt; 评分(4.0) &gt; 加购(3.0) &gt; 点击(2.0) &gt; 浏览(1.0)。
 */
@Getter
public enum BehaviorType {

    /** 浏览商品详情 */
    VIEW("VIEW", "浏览", 1.0),

    /** 点击推荐位或列表项 */
    CLICK("CLICK", "点击", 2.0),

    /** 加入购物车 */
    CART("CART", "加入购物车", 3.0),

    /** 完成购买 */
    PURCHASE("PURCHASE", "购买", 5.0),

    /** 提交评分 */
    RATE("RATE", "评分", 4.0);

    private final String code;
    private final String name;
    private final double weight;

    BehaviorType(String code, String name, double weight) {
        this.code = code;
        this.name = name;
        this.weight = weight;
    }

    /**
     * 按字符串编码还原枚举。
     *
     * @param code 行为编码，忽略大小写与首尾空格
     * @return 对应枚举
     * @throws IllegalArgumentException 编码未知时抛出
     */
    public static BehaviorType of(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("行为类型不能为空");
        }
        String normalized = code.trim().toUpperCase();
        for (BehaviorType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的行为类型: " + code);
    }

    /**
     * 安全解析，未知编码返回 null 而非抛异常，用于容忍脏数据的批量导入场景。
     *
     * @param code 行为编码
     * @return 对应枚举或 null
     */
    public static BehaviorType ofNullable(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (BehaviorType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
