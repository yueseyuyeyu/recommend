package com.deng.recommend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求。
 *
 * <p>全部字段可选：只提交需要改的项，未提交的保持原值。
 * 用户名与密码不在此处修改（用户名是登录标识，密码走上层认证流程）。
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 64, message = "昵称长度须为 1-64 位")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱过长")
    private String email;

    /** 允许留空表示清空，填写则必须是 11 位手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 255, message = "地址过长")
    private String address;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
