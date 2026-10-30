package com.deng.recommend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 层配置：把本地上传目录映射为可访问的静态资源。
 *
 * <p>用户上传的头像存在磁盘上而不是数据库里——图片是二进制大对象，
 * 塞进 varchar 会撑爆单行限制，也让每次查询用户都背着几十 KB 的冗余数据。
 *
 * <p>映射后的访问路径形如 {@code /uploads/avatar/2_xxx.png}。
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebMvcConfig(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();

        // 启动时先把目录建出来。否则 root.toUri() 因为路径不存在，
        // 不会带上结尾斜杠，注册进去的资源位置就成了一个「没有斜杠的前缀」，
        // 结果 /uploads/** 永远匹配不到文件，请求会落到 404。
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("上传目录创建失败，头像将无法访问: {}", root, e);
        }

        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
