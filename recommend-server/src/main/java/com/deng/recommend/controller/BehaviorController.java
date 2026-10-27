package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.dto.BehaviorReportRequest;
import com.deng.recommend.service.BehaviorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户行为上报接口，推荐算法的数据入口。
 *
 * <p>用户 ID 一律从登录态获取，不信任请求体，防止越权伪造他人行为。
 */
@Validated
@RestController
@RequestMapping("/api/behavior")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorService behaviorService;

    /**
     * 上报一条用户行为（浏览/点击/加购/购买/评分）。
     *
     * @param request 行为上报请求
     * @return 空数据成功响应
     */
    @PostMapping("/report")
    public Result<Void> report(@Valid @RequestBody BehaviorReportRequest request) {
        behaviorService.report(SecurityUtils.currentUserId(), request);
        return Result.success(null);
    }
}
