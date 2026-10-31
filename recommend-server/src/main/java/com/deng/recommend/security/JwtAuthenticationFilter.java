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
 * <p>只信任令牌内的信息，<b>不查业务数据库</b>。唯一的额外判断是令牌黑名单——
 * 那是「服务端注销」必需的：黑名单常驻内存，注销时同步写入，
 * 所以每个请求仍然只有一次内存查表，没有 IO。
 *
 * <p>未做「用户被禁用即时生效」：禁用后旧令牌仍可用至过期（24h）。
 * 若需要，可对用户状态做同样的内存态校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

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
