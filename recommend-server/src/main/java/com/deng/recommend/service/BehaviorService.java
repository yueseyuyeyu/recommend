package com.deng.recommend.service;

import com.deng.recommend.dto.BehaviorReportRequest;
import com.deng.recommend.recommend.model.UserItemMatrix;

import java.time.LocalDateTime;

/**
 * 用户行为服务，推荐算法的数据入口。
 */
public interface BehaviorService {

    /**
     * 上报一条用户行为。
     *
     * @param userId  当前登录用户 ID
     * @param request 行为上报请求
     */
    void report(Long userId, BehaviorReportRequest request);

    /**
     * 构建用户-物品行为矩阵。
     *
     * @param since 起始时间，为空表示不限（取全量）
     * @return 行为矩阵
     */
    UserItemMatrix buildMatrix(LocalDateTime since);
}
