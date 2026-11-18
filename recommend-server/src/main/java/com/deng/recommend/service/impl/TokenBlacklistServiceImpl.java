package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.entity.RevokedToken;
import com.deng.recommend.mapper.RevokedTokenMapper;
import com.deng.recommend.security.JwtTokenProvider;
import com.deng.recommend.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌黑名单实现：数据库为唯一真值来源，本地只做「秒级」结果缓存。
 *
 * <p><b>为什么不再用启动时全量加载 + 定期重载</b>：那种做法在多实例下，
 * A 实例的注销要等 B 实例下一次重载才生效，延迟等于重载间隔；
 * 且每个实例都要把整张表灌进内存。
 *
 * <p><b>现在的策略</b>：判定时按 {@code token_hash} 点查数据库（唯一索引，成本极低），
 * 结果在本地缓存 {@code security.jwt.blacklist-cache-ttl-ms}（默认 1000ms）。
 * 因此：
 * <ul>
 *   <li>跨实例注销的生效延迟上界 = 缓存 TTL（默认 1 秒），满足「秒级一致」；</li>
 *   <li>同一令牌在一秒内的重复请求只查一次库，不会给每个请求都加 IO；</li>
 *   <li>不再需要把整表加载进内存，实例内存占用与注销总量无关。</li>
 * </ul>
 *
 * <p>缓存同时保存「已注销」与「未注销」两种判定，避免被频繁探测的无效令牌反复查库。
 * 把 TTL 设为 0 可退化为每次请求直查数据库（强一致、零延迟）。
 */
@Slf4j
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    /** 本地判定缓存上限，超过整体清空，防止被大量随机令牌撑爆内存 */
    private static final int MAX_LOCAL_ENTRIES = 10_000;

    /** token 摘要 → 判定结果（含有效期） */
    private final Map<String, CachedVerdict> localCache = new ConcurrentHashMap<>();

    /** 判定结果缓存时长（毫秒）；<=0 表示不缓存，每次直查数据库 */
    private final long cacheTtlMs;

    private final RevokedTokenMapper revokedTokenMapper;
    private final JwtTokenProvider tokenProvider;

    public TokenBlacklistServiceImpl(RevokedTokenMapper revokedTokenMapper,
                                     JwtTokenProvider tokenProvider,
                                     @Value("${security.jwt.blacklist-cache-ttl-ms:1000}") long cacheTtlMs) {
        this.revokedTokenMapper = revokedTokenMapper;
        this.tokenProvider = tokenProvider;
        this.cacheTtlMs = cacheTtlMs;
    }

    /** 缓存条目：是否已注销 + 该判定何时过期 */
    private record CachedVerdict(boolean revoked, long expireAt) {
    }

    @Override
    public void revoke(String token) {
        Claims claims;
        try {
            claims = tokenProvider.parseToken(token);
        } catch (Exception e) {
            // 令牌本身无效或已过期：它已经用不了，无需入黑名单
            log.debug("注销时令牌无法解析，忽略: {}", e.getMessage());
            return;
        }

        Date expiration = claims.getExpiration();
        if (expiration == null || expiration.before(new Date())) {
            return;
        }

        String hash = sha256(token);
        CachedVerdict cached = localCache.get(hash);
        if (cached != null && cached.revoked()) {
            // 本实例已判定注销，幂等返回
            return;
        }

        LocalDateTime expireAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expiration.getTime()), ZoneId.systemDefault());
        try {
            RevokedToken record = new RevokedToken();
            record.setTokenHash(hash);
            record.setUserId(resolveUserId(claims));
            record.setExpireAt(expireAt);
            revokedTokenMapper.insert(record);
        } catch (Exception e) {
            // 唯一索引冲突（并发重复注销）可忽略；其他异常也不应让退出登录失败
            log.warn("写入令牌黑名单失败，本次仅本地生效: {}", e.getMessage());
        }

        // 本实例立刻生效：缓存到令牌自身过期为止，不必再查库
        localCache.put(hash, new CachedVerdict(true, expiration.getTime()));
        log.info("令牌已注销, userId={}, 有效期至 {}", resolveUserId(claims), expireAt);
    }

    @Override
    public boolean isRevoked(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String hash = sha256(token);
        long now = System.currentTimeMillis();

        CachedVerdict cached = localCache.get(hash);
        if (cached != null && cached.expireAt() > now) {
            return cached.revoked();
        }

        boolean revoked = existsActiveRecord(hash);
        putVerdict(hash, revoked, now);
        return revoked;
    }

    @Override
    public int purgeExpired() {
        int deleted = revokedTokenMapper.delete(new LambdaQueryWrapper<RevokedToken>()
                .lt(RevokedToken::getExpireAt, LocalDateTime.now()));
        // 过期记录已删除，本地判定一并作废，避免继续用旧结果
        localCache.clear();
        if (deleted > 0) {
            log.info("清理过期令牌黑名单 {} 条", deleted);
        }
        return deleted;
    }

    /**
     * 查库判断是否存在未过期的注销记录。
     *
     * <p>用 {@code token_hash} 唯一索引点查，并顺带过滤 {@code expire_at}：
     * 即使清理任务还没跑，过期记录也不会误伤。
     */
    private boolean existsActiveRecord(String hash) {
        try {
            Long count = revokedTokenMapper.selectCount(new LambdaQueryWrapper<RevokedToken>()
                    .eq(RevokedToken::getTokenHash, hash)
                    .gt(RevokedToken::getExpireAt, LocalDateTime.now()));
            return count != null && count > 0;
        } catch (Exception e) {
            // 数据库抖动时按「未注销」放行：注销是安全增强，不能因此让所有请求 401
            log.warn("查询令牌黑名单失败，按未注销处理: {}", e.getMessage());
            return false;
        }
    }

    private void putVerdict(String hash, boolean revoked, long now) {
        if (cacheTtlMs <= 0) {
            return;
        }
        if (localCache.size() > MAX_LOCAL_ENTRIES) {
            localCache.clear();
        }
        localCache.put(hash, new CachedVerdict(revoked, now + cacheTtlMs));
    }

    private Long resolveUserId(Claims claims) {
        Object uid = claims.get("uid");
        return uid == null ? 0L : Long.valueOf(String.valueOf(uid));
    }

    /** 令牌摘要：存摘要而非明文，库泄露也无法直接复用令牌 */
    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
