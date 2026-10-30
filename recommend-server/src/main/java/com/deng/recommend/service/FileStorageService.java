package com.deng.recommend.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务。
 */
public interface FileStorageService {

    /**
     * 保存用户头像。
     *
     * @param userId 当前用户 ID，用于隔离不同用户的文件
     * @param file   上传的图片
     * @return 可直接访问的相对 URL，如 {@code /uploads/avatar/2_ab12cd.png}
     */
    String storeAvatar(Long userId, MultipartFile file);
}
