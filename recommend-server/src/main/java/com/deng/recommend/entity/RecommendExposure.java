package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 推荐曝光流水，对应表 {@code recommend_exposure}。
 *
 * <p>只记「这次把哪些商品推给了谁」，不记录是否点击——
 * 点击沿用已有的 {@code behavior(CLICK)}，靠 user + product + 时间窗关联，
 * 避免同一件事写两遍、两处口径不一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("recommend_exposure")
public class RecommendExposure {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    /** 场景：HOME_FEED / SIMILAR / HOT */
    private String scene;

    /** 在该场景中的位次，从 1 开始；用于分析首屏与深位的差异 */
    private Integer position;

    private LocalDateTime exposedAt;

    public RecommendExposure(Long userId, Long productId, String scene,
                             Integer position, LocalDateTime exposedAt) {
        this.userId = userId;
        this.productId = productId;
        this.scene = scene;
        this.position = position;
        this.exposedAt = exposedAt;
    }
}
