package com.deng.recommend.security;

import com.deng.recommend.entity.User;
import com.deng.recommend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户状态校验缓存：让「禁用用户」对已签发的令牌也能秒级生效。
 *
 * <p>JWT 过滤器原本只信任令牌里的 uid/role，不查用户表——这是无状态的代价：
 * 管理员把某个用户禁用后，他手里的令牌仍能用到过期（最长 24 小时）。
 *
 * <p>这里的折中：按主键点查 {@code user.status}，结果本地缓存
 * {@code security.user-status-cache-ttl-ms}（默认 1000ms）。
 * 于是禁用操作的生效延迟上界是 1 秒，而不是令牌剩余有效期；
 * 同一用户一秒内的多次请求只查一次库。
 *
 * <p>把 TTL 设为 0 表示每次请求都查库（最强一致）；设为负数则完全关闭校验（不推荐）。
 */
@Slf4j
@Component
public class UserStatusCache {

    /** 缓存条目上限，超过整体清空，避免大量用户涌入时无界增长 */
    private static final int MAX_ENTRIES = 10_000;

    private final Map<Long, CachedStatus> cache = new ConcurrentHashMap<>();

    private final long ttlMs;

    private final UserMapper userMapper;

    public UserStatusCache(UserMapper userMapper,
                           @Value("${security.user-status-cache-ttl-ms:1000}") long ttlMs) {
        this.userMapper = userMapper;
        this.ttlMs = ttlMs;
    }

    /** 缓存条目：是否可用 + 判定过期时间 */
    private record CachedStatus(boolean active, long expireAt) {
    }

    /**
     * 判断用户当前是否可用（存在、未删除、status=1）。
     *
     * @param userId 令牌中的用户 ID
     * @return true 表示可以继续认证；用户不存在或已禁用返回 false
     */
    public boolean isActive(Long userId) {
        if (userId == null) {
            return false;
        }
        if (ttlMs < 0) {
            // 显式关闭校验
            return true;
        }

        long now = System.currentTimeMillis();
        CachedStatus cached = cache.get(userId);
        if (cached != null && cached.expireAt() > now) {
            return cached.active();
        }

        boolean active = loadActive(userId);
        if (ttlMs > 0) {
            if (cache.size() > MAX_ENTRIES) {
                cache.clear();
            }
            cache.put(userId, new CachedStatus(active, now + ttlMs));
        }
        return active;
    }

    /** 清除某个用户的缓存，禁用/启用操作后可主动调用以立即生效 */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    private boolean loadActive(Long userId) {
        try {
            // selectById 会自动带上逻辑删除条件，已删除用户查不到
            User user = userMapper.selectById(userId);
            return user != null && Integer.valueOf(1).equals(user.getStatus());
        } catch (Exception e) {
            // 数据库抖动时按「可用」处理，避免一次故障让所有在线用户被踢下线
            log.warn("查询用户状态失败，按可用处理: userId={}, {}", userId, e.getMessage());
            return true;
        }
    }
}
