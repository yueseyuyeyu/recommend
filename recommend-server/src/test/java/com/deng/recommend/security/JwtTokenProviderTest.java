package com.deng.recommend.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * JWT 提供者单元测试。
 *
 * <p>密钥长度校验是启动期唯一的硬性约束：HMAC-SHA256 要求 ≥ 32 字节，
 * 中文按 3 字节计，所以一句 11 个汉字的口令只有 29 字节，必须被拦住。
 */
class JwtTokenProviderTest {

    private static final String VALID_SECRET = "dev-only-jwt-secret-key-0123456789abcdef";

    private static final long ONE_HOUR = 3600_000L;

    @Test
    void constructor_shortSecret_throwsWithByteCount() {
        String chinesePlaceholder = "至少32位的随机字符串";

        Throwable throwable = catchThrowable(() -> new JwtTokenProvider(chinesePlaceholder, ONE_HOUR));

        assertThat(throwable).isInstanceOf(IllegalStateException.class);
        assertThat(throwable.getMessage())
                .contains("JWT 密钥长度不足")
                .contains(chinesePlaceholder.getBytes(StandardCharsets.UTF_8).length + " 字节")
                .contains("32 字节")
                .contains("JWT_SECRET");
    }

    @Test
    void constructor_exactly32Bytes_accepted() {
        String secret = "0123456789abcdef0123456789abcdef";

        JwtTokenProvider provider = new JwtTokenProvider(secret, ONE_HOUR);

        assertThat(provider.getExpirationMillis()).isEqualTo(ONE_HOUR);
    }

    @Test
    void generateToken_thenParse_returnsClaims() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, ONE_HOUR);

        String token = provider.generateToken(7L, "user001", "USER");
        Claims claims = provider.parseToken(token);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(7L);
        assertThat(provider.getUsername(token)).isEqualTo("user001");
        assertThat(claims.get("role")).isEqualTo("USER");
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, ONE_HOUR);
        String token = provider.generateToken(7L, "user001", "USER");
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, -1000L);

        String expired = provider.generateToken(7L, "user001", "USER");

        assertThat(provider.validateToken(expired)).isFalse();
    }
}
