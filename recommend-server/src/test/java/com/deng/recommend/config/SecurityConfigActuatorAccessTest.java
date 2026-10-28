package com.deng.recommend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actuator 端点访问控制测试。
 *
 * <p>健康检查必须匿名可访问（容器编排与监控探针无法携带 JWT），
 * 而 env / configprops / heapdump 等端点会带出环境变量与堆内明文口令，
 * 必须继续要求认证。这里用独立 H2 内存库启动完整上下文，不依赖本机 MySQL；
 * {@code spring.datasource.password} 需显式给值，否则会被
 * {@link DataSourcePasswordValidator} 在启动期拦住。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:actuator_probe;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=test-only"
})
@AutoConfigureMockMvc
class SecurityConfigActuatorAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void health_noToken_accessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void env_noToken_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void cart_noToken_stillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void exposure_includeOnlyHealthAndInfo() {
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
    }
}
