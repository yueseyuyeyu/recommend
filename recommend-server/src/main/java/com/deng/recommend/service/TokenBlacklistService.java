package com.deng.recommend.service;

/**
 * 令牌黑名单服务：支撑服务端注销。
 *
 * <p>JWT 无状态，令牌一旦签发在过期前始终可用。要支持「点退出就真的失效」，
 * 必须有服务端记录。本服务把已注销令牌的摘要落库，并在内存里维持一份集合，
 * 让鉴权过滤器每次请求只需一次内存查表。
 */
public interface TokenBlacklistService {

    /**
     * 注销一个令牌。
     *
     * <p>幂等：重复注销同一令牌不会报错。令牌本身无效或已过期时直接忽略——
     * 它已经用不了了，没有记录的必要。
     *
     * @param token 原始 JWT 字符串
     */
    void revoke(String token);

    /**
     * 判断令牌是否已被注销。
     *
     * <p>纯内存查表，不产生数据库访问。
     */
    boolean isRevoked(String token);

    /**
     * 清理已过期令牌的注销记录。
     *
     * @return 清理条数
     */
    int purgeExpired();
}
