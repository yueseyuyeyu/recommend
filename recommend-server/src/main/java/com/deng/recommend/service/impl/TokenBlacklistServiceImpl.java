package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.entity.RevokedToken;
import com.deng.recommend.mapper.RevokedTokenMapper;
import com.deng.recommend.security.JwtTokenProvider;
import com.deng.recommend.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 令牌黑名单实现：数据库为真值来源，内存缓存承载高频查询。
 *
 * <p><b>为什么不用纯内存</b>：进程重启后内存清空，已注销的令牌会「复活」到过期为止，
 * 这对安全相关的状态不可接受，所以落库。
 *
 * <p><b>为什么还要内存缓存</b>：若每次请求都查库，等于给所有接口加一次 IO；
 * 黑名单只增不减（按过期时间清理），单机场景下内存集合与库完全一致，
 * 因此启动后懒加载一次、注销时同步写入即可。
 *
 * <p>缓存条目自带过期时间：令牌过期后即使记录还在，也不应再拦截。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    /** token 摘要 → 令牌过期时间 */
    private final Map<String, LocalDateTime> revokedCache = new ConcurrentHashMap<>();

    /** 是否已从数据库加载过；懒加载避免启动时依赖数据库可用 */
    private volatile boolean loaded = false;

    private final RevokedTokenMapper revokedTokenMapper;
    private final JwtTokenProvider tokenProvider;

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
        log.info("令牌已注销, userId={}, 有效期至 {}", resolveUserId(claims), expireAt);
    }

    @Override
    public boolean isRevoked(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        ensureLoaded();

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
            List<RevokedToken> rows = revokedTokenMapper.selectList(
                    new LambdaQueryWrapper<RevokedToken>()
                            .gt(RevokedToken::getExpireAt, LocalDateTime.now()));
            for (RevokedToken row : rows) {
                revokedCache.put(row.getTokenHash(), row.getExpireAt());
            }
            loaded = true;
            log.info("令牌黑名单已加载 {} 条有效记录", rows.size());
        }
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
