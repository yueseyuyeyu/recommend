package com.deng.recommend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车条目请求。
 */
@Data
public class CartUpdateRequest {

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer quantity;

    /** 是否勾选结算：1-是，0-否 */
    private Integer checked;
}
