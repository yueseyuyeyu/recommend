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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌黑名单实现：数据库为真值来源，内存缓存承载高频查询。
 *
 * <p><b>为什么不用纯内存</b>：进程重启后内存清空，已注销的令牌会「复活」到过期为止，
 * 这对安全相关的状态不可接受，所以落库。
 *
 * <p><b>为什么还要内存缓存</b>：若每次请求都查库，等于给所有接口加一次 IO；
 * 无状态令牌本应「零查询」，黑名单只增加一次内存查表。
 *
 * <p><b>多实例一致性</b>：其他实例写入的注销记录本实例看不见，
 * 因此每隔 {@code security.jwt.blacklist-reload-interval-ms} 做一次增量重载，
 * 把库中新增的未过期记录并入内存。间隔越长，跨实例的注销生效越慢。
 */
@Slf4j
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    /** token 摘要 → 令牌过期时间 */
    private final Map<String, LocalDateTime> revokedCache = new ConcurrentHashMap<>();

    /** 是否已从数据库加载过；懒加载避免启动时依赖数据库可用 */
    private volatile boolean loaded = false;

    /** 上次与数据库同步的时间戳，用于控制跨实例重载频率 */
    private final AtomicLong lastReloadAt = new AtomicLong(0L);

    /** 跨实例重载间隔（毫秒），<=0 表示只在启动时加载一次 */
    private final long reloadIntervalMs;

    private final RevokedTokenMapper revokedTokenMapper;
    private final JwtTokenProvider tokenProvider;

    public TokenBlacklistServiceImpl(RevokedTokenMapper revokedTokenMapper,
                                     JwtTokenProvider tokenProvider,
                                     @Value("${security.jwt.blacklist-reload-interval-ms:30000}")
                                     long reloadIntervalMs) {
        this.revokedTokenMapper = revokedTokenMapper;
        this.tokenProvider = tokenProvider;
        this.reloadIntervalMs = reloadIntervalMs;
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
        LocalDateTime expireAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expiration.getTime()), ZoneId.systemDefault());

        // 先查缓存，重复注销直接返回，省一次数据库往返
        if (revokedCache.containsKey(hash)) {
            return;
        }

        try {
            RevokedToken record = new RevokedToken();
            record.setTokenHash(hash);
            record.setUserId(resolveUserId(claims));
            record.setExpireAt(expireAt);
            revokedTokenMapper.insert(record);
        } catch (Exception e) {
            // 唯一索引冲突（并发重复注销）可忽略；其他异常也不应让退出登录失败
            log.warn("写入令牌黑名单失败，本次仅内存生效: {}", e.getMessage());
        }

        revokedCache.put(hash, expireAt);
        // 本实例刚同步过，重置计时，避免紧接着再查一次库
        lastReloadAt.set(System.currentTimeMillis());
        log.info("令牌已注销, userId={}, 有效期至 {}", resolveUserId(claims), expireAt);
    }

    @Override
    public boolean isRevoked(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        ensureLoaded();
        reloadIfStale();

        String hash = sha256(token);
        LocalDateTime expireAt = revokedCache.get(hash);
        if (expireAt == null) {
            return false;
        }
        // 令牌已过期，记录留着也没意义，顺手清掉
        if (expireAt.isBefore(LocalDateTime.now())) {
            revokedCache.remove(hash);
            return false;
        }
        return true;
    }

    @Override
    public int purgeExpired() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = revokedTokenMapper.delete(new LambdaQueryWrapper<RevokedToken>()
                .lt(RevokedToken::getExpireAt, now));
        revokedCache.entrySet().removeIf(e -> e.getValue().isBefore(now));
        if (deleted > 0) {
            log.info("清理过期令牌黑名单 {} 条", deleted);
        }
        return deleted;
    }

    /** 首次访问时把库中未过期的记录加载进内存 */
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            List<RevokedToken> rows = loadActiveRecords();
            for (RevokedToken row : rows) {
                revokedCache.put(row.getTokenHash(), row.getExpireAt());
            }
            loaded = true;
            lastReloadAt.set(System.currentTimeMillis());
            log.info("令牌黑名单已加载 {} 条有效记录", rows.size());
        }
    }

    /**
     * 增量重载其他实例写入的注销记录。
     *
     * <p>用 CAS 抢占本次重载权：多线程同时发现过期时只有一个真正查库，其余直接跳过。
     * 重载只是 {@code putIfAbsent}，不覆盖本实例已写入的条目，也不会把已清理的记录带回来。
     */
    private void reloadIfStale() {
        if (reloadIntervalMs <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastReloadAt.get();
        if (now - last < reloadIntervalMs) {
            return;
        }
        if (!lastReloadAt.compareAndSet(last, now)) {
            return;
        }
        try {
            List<RevokedToken> rows = loadActiveRecords();
            for (RevokedToken row : rows) {
                revokedCache.putIfAbsent(row.getTokenHash(), row.getExpireAt());
            }
            log.debug("令牌黑名单增量重载完成，共 {} 条有效记录", rows.size());
        } catch (Exception e) {
            // 重载失败不影响本地已有黑名单，下个周期再试
            log.warn("令牌黑名单增量重载失败: {}", e.getMessage());
        }
    }

    /** 查询库中尚未过期的注销记录 */
    private List<RevokedToken> loadActiveRecords() {
        return revokedTokenMapper.selectList(new LambdaQueryWrapper<RevokedToken>()
                .gt(RevokedToken::getExpireAt, LocalDateTime.now()));
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
