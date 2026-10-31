package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.dto.LoginRequest;
import com.deng.recommend.dto.RegisterRequest;
import com.deng.recommend.service.AuthService;
import com.deng.recommend.service.TokenBlacklistService;
import com.deng.recommend.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、注销。
 *
 * <p>前两者在 {@code SecurityConfig} 中已放行；注销需要携带令牌。
 */
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;

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

    /**
     * 用户注销：把当前令牌加入黑名单，使其立即失效。
     *
     * <p>JWT 无状态，前端删掉令牌只是「本机忘了它」，令牌本身在过期前仍然可用。
     * 真正让令牌失效必须由服务端记录，本接口即为此。
     *
     * <p>幂等且宽容：未携带令牌、令牌已过期或已注销，一律返回成功——
     * 用户点退出时不该看到报错。
     *
     * @param authorization {@code Bearer <token>}，可缺省
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            tokenBlacklistService.revoke(authorization.substring(7));
        }
        return Result.success();
    }
}
