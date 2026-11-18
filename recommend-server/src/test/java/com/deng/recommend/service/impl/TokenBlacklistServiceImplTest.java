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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 令牌黑名单单元测试：注销、幂等、跨实例点查、本地缓存窗口与过期清理。
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

    private TokenBlacklistServiceImpl service(long cacheTtlMs) {
        return new TokenBlacklistServiceImpl(revokedTokenMapper, tokenProvider, cacheTtlMs);
    }

    /** 与实现保持一致的摘要算法，用于断言写入内容 */
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

    @Test
    void revoke_thenIsRevoked_returnsTrueAndStoresHash() throws Exception {
        when(revokedTokenMapper.insert(any(RevokedToken.class))).thenReturn(1);
        TokenBlacklistServiceImpl blacklist = service(1000L);

        String token = tokenProvider.generateToken(1L, "user001", "USER");
        blacklist.revoke(token);

        assertThat(blacklist.isRevoked(token)).isTrue();

        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .hasSize(64)
                .isEqualTo(sha256(token))
                .isNotEqualTo(token);
        assertThat(captor.getValue().getExpireAt()).isAfter(LocalDateTime.now());
        // 本实例注销后直接用本地判定，不再回查数据库
        verify(revokedTokenMapper, times(0)).selectCount(any(Wrapper.class));
    }

    @Test
    void revoke_sameTokenTwice_insertsOnce() {
        when(revokedTokenMapper.insert(any(RevokedToken.class))).thenReturn(1);
        TokenBlacklistServiceImpl blacklist = service(1000L);

        String token = tokenProvider.generateToken(1L, "user001", "USER");
        blacklist.revoke(token);
        blacklist.revoke(token);

        verify(revokedTokenMapper, times(1)).insert(any(RevokedToken.class));
    }

    @Test
    void revoke_expiredToken_isIgnored() {
        TokenBlacklistServiceImpl blacklist = service(1000L);
        String valid = tokenProvider.generateToken(1L, "user001", "USER");
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1000L);
        String expired = expiredProvider.generateToken(1L, "user001", "USER");

        blacklist.revoke(valid);
        blacklist.revoke(expired);

        // 有效令牌写库一次；过期令牌本身已不可用，不写
        verify(revokedTokenMapper, times(1)).insert(any(RevokedToken.class));
    }

    @Test
    void isRevoked_revokedByAnotherInstance_isVisibleImmediately() throws Exception {
        // 本实例内存里没有这条记录，但库里有（另一个实例注销的）
        when(revokedTokenMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        TokenBlacklistServiceImpl blacklist = service(1000L);
        String token = tokenProvider.generateToken(2L, "user002", "USER");

        assertThat(blacklist.isRevoked(token)).isTrue();
        verify(revokedTokenMapper).selectCount(any(Wrapper.class));
    }

    @Test
    void isRevoked_twiceWithinCacheTtl_queriesDatabaseOnce() {
        when(revokedTokenMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        TokenBlacklistServiceImpl blacklist = service(1000L);
        String token = tokenProvider.generateToken(3L, "user003", "USER");

        assertThat(blacklist.isRevoked(token)).isFalse();
        assertThat(blacklist.isRevoked(token)).isFalse();

        verify(revokedTokenMapper, times(1)).selectCount(any(Wrapper.class));
    }

    @Test
    void isRevoked_cacheDisabled_queriesEveryTime() {
        when(revokedTokenMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        TokenBlacklistServiceImpl blacklist = service(0L);
        String token = tokenProvider.generateToken(4L, "user004", "USER");

        blacklist.isRevoked(token);
        blacklist.isRevoked(token);

        verify(revokedTokenMapper, times(2)).selectCount(any(Wrapper.class));
    }

    @Test
    void isRevoked_dbError_treatsAsNotRevoked() {
        when(revokedTokenMapper.selectCount(any(Wrapper.class))).thenThrow(new RuntimeException("db down"));
        TokenBlacklistServiceImpl blacklist = service(0L);
        String token = tokenProvider.generateToken(5L, "user005", "USER");

        // 数据库抖动不能把所有在线用户都踢下线
        assertThat(blacklist.isRevoked(token)).isFalse();
    }

    @Test
    void purgeExpired_deletesExpiredRowsAndClearsLocalCache() {
        when(revokedTokenMapper.delete(any(Wrapper.class))).thenReturn(3);
        TokenBlacklistServiceImpl blacklist = service(1000L);

        assertThat(blacklist.purgeExpired()).isEqualTo(3);
        verify(revokedTokenMapper).delete(any(Wrapper.class));
    }
}
