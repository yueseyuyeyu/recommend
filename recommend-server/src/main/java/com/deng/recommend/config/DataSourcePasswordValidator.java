package com.deng.recommend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

/**
 * 启动期校验数据库口令是否真正注入。
 *
 * <p>{@code application.yml} 中口令写作 {@code ${DB_PASSWORD}}（不留默认值），
 * 但 Spring 的占位符解析器会把无法解析的占位符原样保留成字面量，
 * 结果就是启动阶段一切正常，直到第一次查库才报 {@code Access denied for user}。
 * 这类失败很容易被误判为业务代码缺陷，因此在这里提前拦住。
 */
@Component
@RequiredArgsConstructor
public class DataSourcePasswordValidator implements InitializingBean {

    /** 未解析占位符的起始标记，命中说明环境变量没有注入 */
    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    private final DataSourceProperties dataSourceProperties;

    @Override
    public void afterPropertiesSet() {
        String password = dataSourceProperties.getPassword();
        if (password == null || password.isBlank() || password.contains(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(
                    "数据库口令未注入：请通过环境变量 DB_PASSWORD 提供 MySQL 口令后重新启动（见 docs/07-部署手册.md 第 3 节）");
        }
    }
}
