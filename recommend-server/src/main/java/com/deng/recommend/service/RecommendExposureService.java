package com.deng.recommend.service;

import com.deng.recommend.dto.ExposureReportRequest;
import com.deng.recommend.vo.RecommendCtrVO;

/**
 * 推荐曝光回流服务。
 */
public interface RecommendExposureService {

    /**
     * 记录一次曝光上报。
     *
     * @param userId  当前用户
     * @param request 场景与商品列表（按展示顺序）
     * @return 实际写入条数
     */
    int report(Long userId, ExposureReportRequest request);

    /**
     * 统计窗口内的曝光与点击率。
     *
     * @param days 窗口天数，会被钳制到 1~90
     */
    RecommendCtrVO ctr(int days);
}
