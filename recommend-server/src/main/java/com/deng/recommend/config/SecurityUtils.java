package com.deng.recommend.config;

import com.deng.recommend.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类。
 *
 * <p>Controller 通过本类获取登录用户 ID，
 * 保证「用户只能访问自己的数据」这一越权防护规则可统一实施。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     * @throws com.deng.recommend.common.BusinessException 未登录时抛出
     */
    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new com.deng.recommend.common.BusinessException(
                    com.deng.recommend.common.ResultCode.UNAUTHORIZED);
        }
        return principal.getUserId();
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null。
     *
     * <p>用于「登录与游客都能访问、但登录后效果更好」的接口，
     * 例如首页推荐流：游客走热门补位，登录用户走个性化推荐。
     *
     * @return 用户 ID，或 null
     */
    public static Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal.getUserId();
    }

    /**
     * 获取当前登录用户名。
     */
    public static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new com.deng.recommend.common.BusinessException(
                    com.deng.recommend.common.ResultCode.UNAUTHORIZED);
        }
        return principal.getUsername();
    }
}
