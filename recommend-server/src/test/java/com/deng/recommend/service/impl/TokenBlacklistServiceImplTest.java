package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.deng.recommend.entity.RevokedToken;
import com.deng.recommend.mapper.RevokedTokenMapper;
import com.deng.recommend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 令牌黑名单单元测试：覆盖注销、幂等、跨实例重载与过期清理。
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceImplTest {

    /** HS256 要求至少 32 字节 */
    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long";

    private static final long ONE_HOUR = 3_600_000L;

    @Mock
    private RevokedTokenMapper revokedTokenMapper;

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, ONE_HOUR);
    }

    private TokenBlacklistServiceImpl service(long reloadIntervalMs) {
        return new TokenBlacklistServiceImpl(revokedTokenMapper, tokenProvider, reloadIntervalMs);
    }

    /** 与实现保持一致的摘要算法，用于断言与构造“其他实例写入的记录” */
    private static String sha256(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static RevokedToken revokedRow(String hash) {
        RevokedToken row = new RevokedToken();
        row.setTokenHash(hash);
        row.setUserId(1L);
        row.setExpireAt(LocalDateTime.now().plusHours(1));
        return row;
    }

    @Test
    void revoke_thenIsRevoked_returnsTrueAndStoresHash() throws Exception {
        when(revokedTokenMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(revokedTokenMapper.insert(any(RevokedToken.class))).thenReturn(1);
        TokenBlacklistServiceImpl blacklist = service(30_000L);

        String token = tokenProvider.generateToken(1L, "user001", "USER");
        blacklist.revoke(token);

        assertThat(blacklist.isRevoked(token)).isTrue();

        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenMapper).insert(captor.capture());
        // 只存摘要，不存明文令牌
        assertThat(captor.getValue().getTokenHash())
                .hasSize(64)
                .isEqualTo(sha256(token))
                .isNotEqualTo(token);
        assertThat(captor.getValue().getExpireAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void revoke_sameTokenTwice_insertsOnce() {
        when(revokedTokenMapper.insert(any(RevokedToken.class))).thenReturn(1);
        TokenBlacklistServiceImpl blacklist = service(30_000L);

        String token = tokenProvider.generateToken(1L, "user001", "USER");
        blacklist.revoke(token);
        blacklist.revoke(token);

        verify(revokedTokenMapper, times(1)).insert(any(RevokedToken.class));
    }

    @Test
    void revoke_expiredToken_isIgnored() {
        TokenBlacklistServiceImpl blacklist = service(30_000L);
        String expired = tokenProvider.generateToken(1L, "user001", "USER");
        // 用另一个已过期签发器生成令牌，模拟过期令牌
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1000L);
        String reallyExpired = expiredProvider.generateToken(1L, "user001", "USER");

        blacklist.revoke(expired);
        blacklist.revoke(reallyExpired);

        // 第一个有效令牌会写库一次；过期令牌不写
        verify(revokedTokenMapper, times(1)).insert(any(RevokedToken.class));
    }

    @Test
    void isRevoked_tokenRevokedByAnotherInstance_isVisibleAfterReload() throws Exception {
        String token = tokenProvider.generateToken(2L, "user002", "USER");
        // 初始库中为空：本实例加载后看不到任何注销记录
        when(revokedTokenMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        TokenBlacklistServiceImpl blacklist = service(1L);

        assertThat(blacklist.isRevoked(token)).isFalse();

        // 模拟另一实例写入注销记录，本实例只能通过定期重载看到它
        when(revokedTokenMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(revokedRow(sha256(token))));
        Thread.sleep(20L);

        assertThat(blacklist.isRevoked(token)).isTrue();
    }

    @Test
    void isRevoked_reloadDisabled_doesNotSeeOtherInstances() throws Exception {
        String token = tokenProvider.generateToken(3L, "user003", "USER");
        when(revokedTokenMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        TokenBlacklistServiceImpl blacklist = service(0L);

        assertThat(blacklist.isRevoked(token)).isFalse();

        Thread.sleep(20L);

        // 关闭重载后只认启动时加载的那一份，期间不再查库
        assertThat(blacklist.isRevoked(token)).isFalse();
        verify(revokedTokenMapper, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    void purgeExpired_deletesExpiredRows() {
        when(revokedTokenMapper.delete(any(Wrapper.class))).thenReturn(3);
        TokenBlacklistServiceImpl blacklist = service(30_000L);

        assertThat(blacklist.purgeExpired()).isEqualTo(3);
        verify(revokedTokenMapper).delete(any(Wrapper.class));
    }
}
