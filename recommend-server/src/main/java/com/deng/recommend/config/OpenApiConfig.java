package com.deng.recommend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 接口文档配置。
 *
 * <p>启动后访问 {@code http://localhost:8080/swagger-ui.html} 查看接口文档。
 * 参考项目没有任何接口文档，接口契约只存在于源码里。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI recommendOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("商品推荐系统 API")
                        .description("Spring Boot 3 + MyBatis-Plus + UserCF/ItemCF 推荐引擎")
                        .version("v1.0.0")
                        .license(new License().name("Apache-2.0")));
    }
}
