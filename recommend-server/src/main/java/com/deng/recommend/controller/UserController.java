package com.deng.recommend.controller;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.dto.UpdateProfileRequest;
import com.deng.recommend.service.UserProfileService;
import com.deng.recommend.vo.HistoryVO;
import com.deng.recommend.vo.UserStatsVO;
import com.deng.recommend.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
