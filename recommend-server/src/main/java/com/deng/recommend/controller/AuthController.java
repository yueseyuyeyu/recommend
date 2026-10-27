package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.dto.LoginRequest;
import com.deng.recommend.dto.RegisterRequest;
import com.deng.recommend.service.AuthService;
import com.deng.recommend.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册与登录。
 *
 * <p>接口在 {@code SecurityConfig} 中已放行，无需令牌即可访问；
 * 成功后返回携带 JWT 的用户视图。
 */
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册。
     *
     * @param request 注册请求，含用户名、密码、确认密码与邮箱
     * @return 注册成功的用户视图（含令牌）
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 用户登录。
     *
     * @param request 登录请求，含用户名与密码
     * @return 登录成功的用户视图（含令牌）
     */
    @PostMapping("/login")
    public Result<UserVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}
