package com.deng.recommend.config;

import com.deng.recommend.common.Result;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置：无状态 JWT 鉴权。
 *
 * <p>参考项目新蜂商城使用 {@code HttpSession} + 拦截器维持登录态，
 * 无法水平扩展且不适合前后端分离；本项目改为 JWT 无状态鉴权。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * 密码编码器：BCrypt 加盐哈希。
     *
     * <p>参考项目使用无加盐 MD5（{@code MD5Util.MD5Encode}），
     * 彩虹表可轻易反查，本项目一律使用 BCrypt。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 认证接口公开
                        .requestMatchers("/api/auth/**").permitAll()
                        // 商品浏览、类目、评价、热门与相似推荐公开
                        .requestMatchers(HttpMethod.GET,
                                "/api/products/**",
                                "/api/categories/**",
                                "/api/reviews/**",
                                "/api/recommend/hot",
                                "/api/recommend/similar/**").permitAll()
                        // 接口文档公开
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 后台管理接口仅管理员
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余接口需登录
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, ResultCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 鉴权失败时返回统一 JSON 响应，而不是默认的登录页跳转。
     */
    private void writeJson(jakarta.servlet.http.HttpServletResponse response, ResultCode resultCode)
            throws java.io.IOException {
        response.setStatus(org.springframework.http.HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(resultCode);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}
