package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.LoginRequest;
import com.deng.recommend.dto.RegisterRequest;
import com.deng.recommend.entity.User;
import com.deng.recommend.entity.enums.UserRole;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.security.JwtTokenProvider;
import com.deng.recommend.service.AuthService;
import com.deng.recommend.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 认证服务实现。
 *
 * <p><b>安全要点</b>：
 * <ul>
 *   <li>密码一律 BCrypt 加盐哈希存储，参考项目的无加盐 MD5 已被替换。</li>
 *   <li>登录失败统一提示「用户名或密码错误」，不区分用户是否存在，防止账号枚举。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_MISMATCH);
        }

        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(UserRole.USER.getCode());
        user.setStatus(1);
        userMapper.insert(user);

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return toUserVO(user, true);
    }

    @Override
    public UserVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        // 用户不存在与密码错误返回同一提示，避免泄露账号是否存在
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 更新最后登录时间
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);

        return toUserVO(user, true);
    }

    private UserVO toUserVO(User user, boolean withToken) {
        UserVO.UserVOBuilder builder = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(user.getRole());
        if (withToken && StringUtils.hasText(user.getUsername())) {
            builder.token(jwtTokenProvider.generateToken(
                    user.getId(), user.getUsername(), UserRole.of(user.getRole()).name()));
        }
        return builder.build();
    }
}
