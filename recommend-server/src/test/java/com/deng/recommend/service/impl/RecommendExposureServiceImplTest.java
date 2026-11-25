package com.deng.recommend.service.impl;

import com.deng.recommend.dto.ExposureReportRequest;
import com.deng.recommend.entity.RecommendExposure;
import com.deng.recommend.mapper.RecommendExposureMapper;
import com.deng.recommend.vo.RecommendCtrVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendExposureServiceImplTest {

    @Mock
    private RecommendExposureMapper exposureMapper;

    @InjectMocks
    private RecommendExposureServiceImpl service;

    private static ExposureReportRequest request(String scene, Long... ids) {
        ExposureReportRequest req = new ExposureReportRequest();
        req.setScene(scene);
        req.setProductIds(new ArrayList<>(List.of(ids)));
        return req;
    }

    @Test
    void report_buildsPositionInDisplayOrderAndUppercasesScene() {
        when(exposureMapper.insertBatch(anyList())).thenReturn(2);

        int inserted = service.report(9L, request("home_feed", 101L, 102L));

        ArgumentCaptor<List<RecommendExposure>> captor = ArgumentCaptor.forClass(List.class);
        verify(exposureMapper).insertBatch(captor.capture());

        List<RecommendExposure> rows = captor.getValue();
        assertThat(inserted).isEqualTo(2);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getPosition()).isEqualTo(1);
        assertThat(rows.get(1).getPosition()).isEqualTo(2);
        // 场景统一大写，避免前端大小写不一致把同一场景统计成两份
        assertThat(rows.get(0).getScene()).isEqualTo("HOME_FEED");
        assertThat(rows.get(0).getUserId()).isEqualTo(9L);
    }

    @Test
    void report_ignoresNullUserAndEmptyList() {
        assertThat(service.report(null, request("HOME_FEED", 1L))).isZero();
        assertThat(service.report(9L, request("HOME_FEED"))).isZero();
        verify(exposureMapper, never()).insertBatch(anyList());
    }

    @Test
    void report_capsAtFiftyItems() {
        when(exposureMapper.insertBatch(anyList())).thenReturn(50);
        Long[] ids = new Long[80];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = (long) (i + 1);
        }

        service.report(9L, request("HOME_FEED", ids));

        ArgumentCaptor<List<RecommendExposure>> captor = ArgumentCaptor.forClass(List.class);
        verify(exposureMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(50);
    }

    @Test
    void ctr_computesRatioAndClampsWindow() {
        when(exposureMapper.countExposures(any(LocalDateTime.class))).thenReturn(200L);
        when(exposureMapper.countClickedExposures(any(LocalDateTime.class))).thenReturn(30L);

        RecommendCtrVO vo = service.ctr(7);

        assertThat(vo.getExposures()).isEqualTo(200L);
        assertThat(vo.getClicked()).isEqualTo(30L);
        assertThat(vo.getCtr()).isCloseTo(0.15, within(1e-9));

        // 越界天数被钳制：0/负数 -> 1 天，过大 -> 90 天
        assertThat(service.ctr(0).getDays()).isEqualTo(1);
        assertThat(service.ctr(999).getDays()).isEqualTo(90);
    }

    @Test
    void ctr_noExposure_returnsZeroWithoutDivideError() {
        when(exposureMapper.countExposures(any(LocalDateTime.class))).thenReturn(0L);

        RecommendCtrVO vo = service.ctr(7);

        assertThat(vo.getCtr()).isZero();
        assertThat(vo.getClicked()).isZero();
        // 没有曝光就不该再去查点击，省一次全表相关子查询
        verify(exposureMapper, never()).countClickedExposures(any(LocalDateTime.class));
    }
}
