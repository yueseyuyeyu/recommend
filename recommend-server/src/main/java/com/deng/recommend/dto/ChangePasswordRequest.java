package com.deng.recommend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求。
 *
 * <p>必须提供原密码：拿到令牌不等于知道密码，
 * 否则令牌一旦泄露，攻击者可以直接改密把真正的用户踢出去。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 20, message = "新密码长度需为 6~20 位")
    private String newPassword;
}
