package com.deng.recommend.security;

import com.deng.recommend.entity.enums.UserRole;
import com.deng.recommend.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权过滤器：解析 {@code Authorization: Bearer <token>} 并建立安全上下文。
 *
 * <p>只信任令牌内的信息，<b>不查业务数据库</b>。两个例外都与安全相关：
 * <ul>
 *   <li>令牌黑名单：支撑「服务端注销」，判定结果本地缓存 1 秒，跨实例秒级一致；</li>
 *   <li>用户状态：支撑「禁用即时生效」，管理员禁用后旧令牌最多 1 秒失效。</li>
 * </ul>
 * 两者都是一次点查 + 短缓存，不会给每个请求都加固定的 IO。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserStatusCache userStatusCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            // 已注销的令牌：不再建立安全上下文，后续按未登录处理（401）
            if (tokenBlacklistService.isRevoked(token)) {
                log.debug("令牌已注销，跳过鉴权: {}", request.getRequestURI());
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            try {
                Claims claims = tokenProvider.parseToken(token);
                Long userId = claims.get("uid", Number.class) == null
                        ? null : claims.get("uid", Number.class).longValue();
                String username = claims.getSubject();
                String roleName = claims.get("role", String.class);

                // 用户被禁用或删除后，已签发的令牌最多再存活一个缓存 TTL（默认 1 秒）
                if (!userStatusCache.isActive(userId)) {
                    log.debug("用户已禁用或不存在，跳过鉴权: userId={}", userId);
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                UserPrincipal principal = new UserPrincipal(
                        userId, username, null, UserRole.valueOf(roleName), true);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.debug("JWT 鉴权失败，清空安全上下文: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取令牌。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
