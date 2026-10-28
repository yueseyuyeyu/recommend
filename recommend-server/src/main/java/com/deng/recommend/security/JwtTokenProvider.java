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

    /** RFC 7518 §3.2：HMAC-SHA 算法要求密钥长度 ≥ 256 bit */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    /** 令牌有效期（毫秒），默认 24 小时 */
    private final long expirationMillis;

    public JwtTokenProvider(@Value("${security.jwt.secret}") String secret,
                            @Value("${security.jwt.expiration:86400000}") long expirationMillis) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        // jjwt 自己的 WeakKeyException 只说“密钥不够强”，看不出该改哪个变量、差多少字节，
        // 因此启动时直接给出可操作的中文提示，避免部署时才发现启动失败。
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT 密钥长度不足：当前 " + keyBytes.length
                    + " 字节，HMAC-SHA256 要求至少 " + MIN_SECRET_BYTES + " 字节。"
                    + "请通过环境变量 JWT_SECRET 注入随机串（中文占 3 字节，别直接用中文句子），"
                    + "例如 PowerShell: $env:JWT_SECRET = -join ((1..48) | ForEach-Object "
                    + "{ [char](Get-Random -Min 33 -Max 126) })");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
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
