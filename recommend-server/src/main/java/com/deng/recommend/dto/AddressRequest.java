package com.deng.recommend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 修改收货地址请求。
 */
@Data
public class AddressRequest {

    @NotBlank(message = "收货人不能为空")
    @Size(max = 64, message = "收货人过长")
    private String receiverName;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String receiverPhone;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址过长")
    private String detailAddress;

    /** 是否设为默认地址；不传时由服务层决定（首条自动成为默认） */
    private Boolean isDefault;
}
