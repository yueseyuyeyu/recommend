package com.deng.recommend.service.impl;

import com.deng.recommend.service.LoginAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流的内存实现。
 *
 * <p><b>为什么按「用户名 + IP」</b>：只按用户名，攻击者连续输错就能把真实用户锁死；
 * 只按 IP，对方换出口就失效。两者组合覆盖了最常见的定向爆破。
 *
 * <p><b>为什么计数窗口与锁定分离</b>：窗口（默认 10 分钟）内累计到阈值才锁，
 * 锁定时间（默认 10 分钟）独立计算。正常用户偶尔输错一两次不会受影响，
 * 而持续试探会越来越难。
 *
 * <p>内存有界：键数超过上限直接整体清空，避免被海量伪造用户名打爆内存。
 * 清空的代价是重置计数，比 OOM 好接受。
 */
@Slf4j
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** 键数量上限，超过即整体清空 */
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 触发锁定的失败次数 */
    private final int maxAttempts;

    /** 计数窗口（秒），窗口外的失败不计入 */
    private final long windowSeconds;

    /** 锁定时长（秒） */
    private final long blockSeconds;

    public LoginAttemptServiceImpl(
            @Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.window-seconds:600}") long windowSeconds,
            @Value("${security.login.block-seconds:600}") long blockSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.blockSeconds = blockSeconds;
    }

    @Override
    public boolean isBlocked(String username, String clientIp) {
        return remainingBlockSeconds(username, clientIp) > 0;
    }

    @Override
    public long remainingBlockSeconds(String username, String clientIp) {
        Attempt attempt = attempts.get(key(username, clientIp));
        if (attempt == null) {
            return 0;
        }
        long blockedUntil = attempt.blockedUntil;
        long now = System.currentTimeMillis();
        return blockedUntil > now ? (blockedUntil - now) / 1000 + 1 : 0;
    }

    @Override
    public void recordFailure(String username, String clientIp) {
        if (attempts.size() > MAX_ENTRIES) {
            log.warn("登录失败计数表超过 {} 条，整体清空以防内存膨胀", MAX_ENTRIES);
            attempts.clear();
        }

        long now = System.currentTimeMillis();
        Attempt attempt = attempts.computeIfAbsent(key(username, clientIp), k -> new Attempt());

        synchronized (attempt) {
            // 窗口已过：重新开始计数，否则昨天的失败会累积到今天的正常用户头上
            if (now - attempt.firstFailureAt > windowSeconds * 1000L) {
                attempt.count = 0;
                attempt.firstFailureAt = now;
            }
            if (attempt.count == 0) {
                attempt.firstFailureAt = now;
            }
            attempt.count++;

            if (attempt.count >= maxAttempts) {
                attempt.blockedUntil = now + blockSeconds * 1000L;
                log.warn("登录失败次数达到阈值，锁定 {} 秒: key={}", blockSeconds, key(username, clientIp));
            }
        }
    }

    @Override
    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    private String key(String username, String clientIp) {
        return (username == null ? "-" : username) + "|" + (clientIp == null ? "-" : clientIp);
    }

    /** 单个键的计数状态，字段在 synchronized 块内读写 */
    private static final class Attempt {
        private int count;
        private long firstFailureAt;
        private long blockedUntil;
    }
}
