package com.deng.recommend.service;

import com.deng.recommend.dto.LoginRequest;
import com.deng.recommend.dto.RegisterRequest;
import com.deng.recommend.vo.UserVO;

/**
 * 认证服务。
 */
public interface AuthService {

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 注册成功的用户视图（含令牌）
     */
    UserVO register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return 登录成功的用户视图（含令牌）
     */
    UserVO login(LoginRequest request);
}
