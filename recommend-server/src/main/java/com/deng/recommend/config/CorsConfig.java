package com.deng.recommend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置：只放行白名单内的来源。
 *
 * <p><b>为什么不能写通配</b>：本项目同时开启了 {@code allowCredentials}，
 * 「任意来源 + 允许携带凭据」意味着任何网站都能代表已登录用户发请求——
 * 这是 CORS 最典型的配置错误。{@code allowedOriginPatterns("*")} 还会把
 * {@code Access-Control-Allow-Origin} 回显成请求方域名，表面看只是"允许跨域"，
 * 实际等于对所有站点敞开。
 *
 * <p>开发期浏览器走 Vite 代理（同源），本就不需要 CORS；这里的白名单是为
 * 直连 8080 调试准备的。来源可通过 {@code CORS_ALLOWED_ORIGINS} 覆盖，
 * 形如 {@code https://a.com,https://b.com}。
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 精确匹配而非通配：白名单之外一律不放行
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
