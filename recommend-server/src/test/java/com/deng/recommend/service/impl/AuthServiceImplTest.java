package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.LoginRequest;
import com.deng.recommend.dto.RegisterRequest;
import com.deng.recommend.entity.User;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.security.JwtTokenProvider;
import com.deng.recommend.vo.UserVO;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtTokenProvider);
    }

    private static BusinessException catchBusinessException(ThrowingCallable callable) {
        Throwable throwable = catchThrowable(callable);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }

    private static RegisterRequest registerRequest(String password, String confirmPassword) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        request.setEmail("alice@example.com");
        return request;
    }

    private static LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret123");
        return request;
    }

    private static User user(int status) {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash("stored-hash");
        user.setNickname("alice");
        user.setRole(1);
        user.setStatus(status);
        return user;
    }

    @Test
    void register_passwordMismatch_throwsPasswordMismatch() {
        BusinessException ex = catchBusinessException(
                () -> authService.register(registerRequest("secret123", "other123")));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PASSWORD_MISMATCH.getCode());
        verifyNoInteractions(userMapper, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void register_duplicateUsername_throwsUsernameExists() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException ex = catchBusinessException(
                () -> authService.register(registerRequest("secret123", "secret123")));

        assertThat(ex.getCode()).isEqualTo(ResultCode.USERNAME_EXISTS.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void register_validRequest_encodesPassword() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-hash");
        when(userMapper.insert(any(User.class))).thenReturn(1);
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("jwt-token");

        UserVO vo = authService.register(registerRequest("secret123", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(passwordEncoder).encode("secret123");
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(captor.getValue().getRole()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(vo.getToken()).isEqualTo("jwt-token");
        assertThat(vo.getUsername()).isEqualTo("alice");
    }

    @Test
    void login_wrongPassword_throwsUsernameOrPasswordError() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(1));
        when(passwordEncoder.matches("secret123", "stored-hash")).thenReturn(false);

        BusinessException ex = catchBusinessException(() -> authService.login(loginRequest()));

        assertThat(ex.getCode()).isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void login_userNotFound_throwsUsernameOrPasswordError() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException ex = catchBusinessException(() -> authService.login(loginRequest()));

        assertThat(ex.getCode()).isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode());
    }

    @Test
    void login_disabledUser_throwsUserDisabled() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(0));
        when(passwordEncoder.matches("secret123", "stored-hash")).thenReturn(true);

        BusinessException ex = catchBusinessException(() -> authService.login(loginRequest()));

        assertThat(ex.getCode()).isEqualTo(ResultCode.USER_DISABLED.getCode());
    }

    @Test
    void login_success_returnsTokenAndUpdatesLastLoginTime() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(1));
        when(passwordEncoder.matches("secret123", "stored-hash")).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, "alice", "USER")).thenReturn("jwt-token");

        UserVO vo = authService.login(loginRequest());

        assertThat(vo.getToken()).isEqualTo("jwt-token");
        assertThat(vo.getUsername()).isEqualTo("alice");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getLastLoginTime()).isNotNull();
    }
}
