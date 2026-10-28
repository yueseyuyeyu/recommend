package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deng.recommend.entity.enums.BehaviorType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户行为实体，对应表 {@code behavior}。
 *
 * <p><b>本表是推荐算法的唯一数据源</b>，也是参考项目中完全缺失的部分——
 * 原房屋租赁系统是纯 CRUD 管理系统，不采集任何用户行为数据。
 */
@Data
@TableName("behavior")
public class Behavior {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 商品 ID */
    private Long productId;

    /** 行为类型编码，取值见 {@link BehaviorType} */
    private String behaviorType;

    /** 行为权重，冗余存储以避免推荐计算时重复查枚举 */
    private Double weight;

    /** 评分值 1–5，仅 RATE 类型有值 */
    private Integer rating;

    /** 行为发生时间 */
    private LocalDateTime createdAt;

    /**
     * 按行为类型自动填充权重，保证写入数据时权重与类型一致。
     */
    public void applyType(BehaviorType type) {
        this.behaviorType = type.getCode();
        this.weight = type.getWeight();
    }
}
