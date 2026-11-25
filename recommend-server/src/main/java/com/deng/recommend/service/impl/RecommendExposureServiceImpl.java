package com.deng.recommend.service.impl;

import com.deng.recommend.dto.ExposureReportRequest;
import com.deng.recommend.entity.RecommendExposure;
import com.deng.recommend.mapper.RecommendExposureMapper;
import com.deng.recommend.service.RecommendExposureService;
import com.deng.recommend.vo.RecommendCtrVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 曝光回流实现。
 *
 * <p>写入用一条多值 INSERT；上报失败只记日志不影响前端——
 * 曝光统计是观测手段，不能因为它出错就拖累推荐展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendExposureServiceImpl implements RecommendExposureService {

    /** 单次上报上限，与 DTO 校验保持一致，防止绕过校验直接调服务 */
    private static final int MAX_ITEMS = 50;

    /** 统计窗口上限：再长的窗口意义有限，却会拖慢全表扫描 */
    private static final int MAX_DAYS = 90;

    private final RecommendExposureMapper exposureMapper;

    @Override
    public int report(Long userId, ExposureReportRequest request) {
        if (userId == null || request == null || request.getProductIds() == null) {
            return 0;
        }
        List<Long> productIds = request.getProductIds();
        if (productIds.isEmpty()) {
            return 0;
        }

        String scene = request.getScene() == null ? "UNKNOWN" : request.getScene().trim().toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        List<RecommendExposure> rows = new ArrayList<>(Math.min(productIds.size(), MAX_ITEMS));
        int position = 1;
        for (Long productId : productIds) {
            if (productId == null) {
                continue;
            }
            if (rows.size() >= MAX_ITEMS) {
                break;
            }
            rows.add(new RecommendExposure(userId, productId, scene, position++, now));
        }
        if (rows.isEmpty()) {
            return 0;
        }

        int inserted = exposureMapper.insertBatch(rows);
        log.debug("曝光上报成功: userId={}, scene={}, 条数={}", userId, scene, inserted);
        return inserted;
    }

    @Override
    public RecommendCtrVO ctr(int days) {
        int window = Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDateTime since = LocalDateTime.now().minusDays(window);

        long exposures = exposureMapper.countExposures(since);
        long clicked = exposures == 0 ? 0 : exposureMapper.countClickedExposures(since);

        return RecommendCtrVO.builder()
                .days(window)
                .since(since)
                .exposures(exposures)
                .clicked(clicked)
                .ctr(exposures == 0 ? 0.0 : clicked / (double) exposures)
                .build();
    }
}
