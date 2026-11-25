package com.deng.recommend.service;

/**
 * 登录失败限流：防止密码被在线穷举。
 *
 * <p>按键为「用户名 + 客户端 IP」。只用用户名会把受害者锁在门外（攻击者故意输错），
 * 只用 IP 则对分布式来源无效，两者组合是这一层的现实折中。
 *
 * <p>状态存在内存里：单机部署够用，进程重启即清零。若要多实例共享，
 * 需要换成 Redis 或数据库计数——但如果真要防大规模爆破，前面还应有网关级限流。
 */
public interface LoginAttemptService {

    /**
     * 是否处于锁定期。
     */
    boolean isBlocked(String username, String clientIp);

    /**
     * 剩余锁定秒数，未锁定返回 0。
     */
    long remainingBlockSeconds(String username, String clientIp);

    /**
     * 记录一次失败；达到阈值后进入锁定期。
     */
    void recordFailure(String username, String clientIp);

    /**
     * 登录成功：清空该键的失败计数。
     */
    void recordSuccess(String username, String clientIp);
}
