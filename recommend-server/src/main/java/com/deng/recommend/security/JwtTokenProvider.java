package com.deng.recommend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌签发与校验。
 *
 * <p>参考项目完全没有会话管理——登录成功后仅靠界面跳转维持"登录态"，
 * 切换界面不做二次校验，存在严重越权风险。本项目统一使用 JWT 做无状态鉴权。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    /** 令牌有效期（毫秒），默认 24 小时 */
    private final long expirationMillis;

    public JwtTokenProvider(@Value("${security.jwt.secret}") String secret,
                            @Value("${security.jwt.expiration:86400000}") long expirationMillis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /**
     * 签发令牌。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 校验令牌并解析载荷。
     *
     * @param token 令牌
     * @return 载荷
     * @throws JwtException 令牌无效或已过期
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验令牌是否有效。
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从令牌中提取用户 ID。
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object uid = claims.get("uid");
        return uid == null ? null : Long.valueOf(String.valueOf(uid));
    }

    /**
     * 从令牌中提取用户名。
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
