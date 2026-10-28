package com.deng.recommend.recommend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行为记录，推荐算法的最小输入单元。
 *
 * <p>与数据库实体 {@link com.deng.recommend.entity.Behavior} 解耦：
 * 算法层只依赖本记录，便于单元测试中不依赖数据库即可构造矩阵。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorRecord {

    private Long userId;

    private Long productId;

    /** 行为加权分，取自 {@link com.deng.recommend.entity.enums.BehaviorType} */
    private Double weight;

    /** 行为发生时间，用于训练集/测试集划分 */
    private java.time.LocalDateTime occurredAt;

    public BehaviorRecord(Long userId, Long productId, Double weight) {
        this.userId = userId;
        this.productId = productId;
        this.weight = weight;
    }
}
