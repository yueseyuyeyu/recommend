package com.deng.recommend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交评价请求。
 */
@Data
public class ReviewRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 关联订单号，可为空 */
    private String orderNo;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围为 1-5")
    @Max(value = 5, message = "评分范围为 1-5")
    private Integer rating;

    private String content;
}
