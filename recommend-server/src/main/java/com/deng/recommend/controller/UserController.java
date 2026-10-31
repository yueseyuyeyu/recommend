package com.deng.recommend.controller;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.dto.ChangePasswordRequest;
import com.deng.recommend.dto.UpdateProfileRequest;
import com.deng.recommend.service.FileStorageService;
import com.deng.recommend.service.TokenBlacklistService;
import com.deng.recommend.service.UserProfileService;
import com.deng.recommend.vo.HistoryVO;
import com.deng.recommend.vo.UserStatsVO;
import com.deng.recommend.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心接口。
 *
 * <p>所有接口都从登录态取 {@code userId}，不接受前端传入用户 ID，
 * 因此不存在「改 URL 就能看别人资料」的越权问题。
 */
@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final FileStorageService fileStorageService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 上传头像。
     *
     * <p>只负责把图片存到磁盘并返回可访问 URL，并不会自动写入用户资料——
     * 前端拿到 URL 后放进表单，用户点「保存」才真正生效，
     * 这样用户选错图片时还能取消。
     *
     * @param file 图片文件，支持 jpg/png/gif/webp，最大 2MB
     * @return 图片访问地址，如 {@code /uploads/avatar/2_ab12cd.png}
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileStorageService.storeAvatar(SecurityUtils.currentUserId(), file));
    }

    /**
     * 查询我的资料。
     */
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.success(userProfileService.getProfile(SecurityUtils.currentUserId()));
    }

    /**
     * 修改我的资料（昵称、邮箱、手机号、地址、头像）。
     *
     * <p>只提交需要修改的字段即可，未提交的保持原值。
     */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userProfileService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    /**
     * 修改密码。
     *
     * <p>成功后当前令牌会立即作废（加入黑名单），前端应引导重新登录——
     * 改密后继续沿用旧令牌，等于改密没生效。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        userProfileService.changePassword(SecurityUtils.currentUserId(), request);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            tokenBlacklistService.revoke(authorization.substring(7));
        }
        return Result.success();
    }

    /**
     * 个人中心顶部的待办数量。
     */
    @GetMapping("/stats")
    public Result<UserStatsVO> stats() {
        return Result.success(userProfileService.stats(SecurityUtils.currentUserId()));
    }

    /**
     * 行为足迹：浏览过 / 买过的商品。
     *
     * @param type     VIEW（默认，含点击）或 PURCHASE
     * @param pageNum  页码
     * @param pageSize 每页条数
     */
    @GetMapping("/history")
    public Result<PageResult<HistoryVO>> history(@RequestParam(defaultValue = "VIEW") String type,
                                                 @RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "12") long pageSize) {
        return Result.success(userProfileService.history(
                SecurityUtils.currentUserId(), type, pageNum, pageSize));
    }
}
