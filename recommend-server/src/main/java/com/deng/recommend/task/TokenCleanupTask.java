package com.deng.recommend.task;

import com.deng.recommend.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 令牌黑名单清理任务。
 *
 * <p>黑名单只增不减会一直长：每条记录在令牌过期后就没用了。
 * 每小时扫一次，把过期记录删掉，内存缓存同步收缩。
 *
 * <p>频率不需要高——清理延迟一天也只是多占几行存储，不影响鉴权正确性
 * （判断时还会看记录里的过期时间）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupTask {

    private final TokenBlacklistService tokenBlacklistService;

    @Scheduled(
            initialDelayString = "${security.jwt.cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${security.jwt.cleanup-interval-ms:3600000}"
    )
    public void purgeExpiredTokens() {
        try {
            tokenBlacklistService.purgeExpired();
        } catch (Exception e) {
            log.error("令牌黑名单清理任务执行失败", e);
        }
    }
}
