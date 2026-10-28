package com.deng.recommend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 行为上报请求。
 */
@Data
public class BehaviorReportRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 行为类型：VIEW / CLICK / CART / PURCHASE / RATE */
    @NotNull(message = "行为类型不能为空")
    private String behaviorType;

    /** 评分 1-5，仅 RATE 类型必填 */
    private Integer rating;
}
