package com.deng.recommend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建订单请求。
 */
@Data
public class OrderCreateRequest {

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "联系电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddr;
}
