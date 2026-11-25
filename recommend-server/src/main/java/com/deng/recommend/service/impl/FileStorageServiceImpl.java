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
import java.nio.file.StandardOpenOption;
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
        // 相对路径按进程工作目录解析，IDEA 运行与命令行运行会落到不同目录，
        // 打印出来可以避免「换个方式启动后旧头像全 404」这种困惑
        log.info("头像存储目录：{}", this.avatarDir);
    }

    @Override
    public String storeAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片不能超过 2MB");
        }

        // 一次性读进内存：上限 2MB，比「先流式写盘再回读校验」简单且不会留脏文件
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "读取上传文件失败，请重试");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 jpg / png / gif / webp 格式");
        }

        // 扩展名可以随便改，必须再看文件头。
        // 否则把一个 HTML/脚本改名成 .png 就能存进可访问的目录，
        // 配合浏览器的内容嗅探会变成存储型 XSS 的入口。
        String detected = detectImageType(content);
        if (detected == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件内容不是有效的图片");
        }
        // 以实际类型为准命名，避免「.png 里装着 jpeg」这类不一致
        extension = detected;

        String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        try {
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(filename).normalize();
            // 二次校验：解析后的路径必须仍在上传目录内
            if (!target.startsWith(avatarDir)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "非法的文件名");
            }
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("头像保存失败: userId={}, filename={}", userId, filename, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "图片保存失败，请稍后重试");
        }

        return "/uploads/avatar/" + filename;
    }

    /**
     * 按文件头识别真实图片类型，认不出返回 null。
     *
     * <p>只认四种：JPEG(FF D8 FF)、PNG(89 50 4E 47)、GIF(47 49 46 38)、
     * WEBP(RIFF....WEBP)。这几条魔数足够挡住「改名成图片的其它文件」。
     */
    private String detectImageType(byte[] content) {
        if (content == null || content.length < 12) {
            return null;
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return "png";
        }
        if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F' && content[3] == '8') {
            return "gif";
        }
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "webp";
        }
        return null;
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
