package com.deng.recommend.service.impl;

import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现。
 *
 * <p><b>安全要点</b>：
 * <ul>
 *   <li>只接受白名单扩展名，不信任客户端传来的 Content-Type</li>
 *   <li>文件名完全由服务端生成（用户 ID + UUID），不使用原始文件名，
 *       避免 {@code ../../evil.jsp} 这类路径穿越与可执行后缀</li>
 *   <li>限制 2MB，防止有人拿上传接口当网盘</li>
 * </ul>
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 单文件大小上限：2MB */
    private static final long MAX_SIZE = 2L * 1024 * 1024;

    private final Path avatarDir;

    public FileStorageServiceImpl(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.avatarDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("avatar");
    }

    @Override
    public String storeAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片不能超过 2MB");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 jpg / png / gif / webp 格式");
        }

        String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        try {
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(filename).normalize();
            // 二次校验：解析后的路径必须仍在上传目录内
            if (!target.startsWith(avatarDir)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "非法的文件名");
            }
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("头像保存失败: userId={}, filename={}", userId, filename, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "图片保存失败，请稍后重试");
        }

        return "/uploads/avatar/" + filename;
    }

    /**
     * 取扩展名并转小写，取不到时返回空串（随后会被白名单拒绝）。
     */
    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String name = originalFilename.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
