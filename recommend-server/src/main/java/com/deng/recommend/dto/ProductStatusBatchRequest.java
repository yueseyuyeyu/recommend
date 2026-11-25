package com.deng.recommend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 商品批量上下架请求。
 */
@Data
public class ProductStatusBatchRequest {

    @NotEmpty(message = "请选择要操作的商品")
    @Size(max = 200, message = "单次最多操作 200 个商品")
    private List<Long> ids;

    /** 1-上架，0-下架 */
    @NotNull(message = "请指定目标状态")
    private Integer status;
}
