package com.deng.recommend.service.impl;

import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.dto.BehaviorReportRequest;
import com.deng.recommend.entity.Behavior;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.ProductMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehaviorServiceImplTest {

    @Mock
    private BehaviorMapper behaviorMapper;

    @Mock
    private ProductMapper productMapper;

    private BehaviorServiceImpl behaviorService;

    @BeforeEach
    void setUp() {
        behaviorService = new BehaviorServiceImpl(behaviorMapper, productMapper, new RecommendProperties());
    }

    private static BusinessException catchBusinessException(ThrowingCallable callable) {
        Throwable throwable = catchThrowable(callable);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }

    private static BehaviorReportRequest request(String behaviorType, Integer rating) {
        BehaviorReportRequest request = new BehaviorReportRequest();
        request.setProductId(100L);
        request.setBehaviorType(behaviorType);
        request.setRating(rating);
        return request;
    }

    private static Product product() {
        Product product = new Product();
        product.setId(100L);
        product.setName("test-product");
        product.setStatus(1);
        return product;
    }

    @Test
    void report_unknownBehaviorType_throwsParamError() {
        BusinessException ex = catchBusinessException(
                () -> behaviorService.report(1L, request("UNKNOWN", null)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verifyNoInteractions(productMapper, behaviorMapper);
    }

    @Test
    void report_productNotFound_throwsProductNotFound() {
        when(productMapper.selectById(100L)).thenReturn(null);

        BusinessException ex = catchBusinessException(
                () -> behaviorService.report(1L, request("VIEW", null)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
        verify(behaviorMapper, never()).insert(any(Behavior.class));
    }

    @Test
    void report_rateWithoutRating_throwsParamError() {
        when(productMapper.selectById(100L)).thenReturn(product());

        BusinessException ex = catchBusinessException(
                () -> behaviorService.report(1L, request("RATE", null)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(behaviorMapper, never()).insert(any(Behavior.class));
    }

    @Test
    void report_rateWithOutOfRangeRating_throwsParamError() {
        when(productMapper.selectById(100L)).thenReturn(product());

        BusinessException ex = catchBusinessException(
                () -> behaviorService.report(1L, request("RATE", 6)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    void report_view_setsWeightOne() {
        when(productMapper.selectById(100L)).thenReturn(product());
        when(behaviorMapper.insert(any(Behavior.class))).thenReturn(1);

        behaviorService.report(1L, request("VIEW", null));

        ArgumentCaptor<Behavior> captor = ArgumentCaptor.forClass(Behavior.class);
        verify(behaviorMapper).insert(captor.capture());
        Behavior behavior = captor.getValue();
        assertThat(behavior.getUserId()).isEqualTo(1L);
        assertThat(behavior.getProductId()).isEqualTo(100L);
        assertThat(behavior.getBehaviorType()).isEqualTo("VIEW");
        assertThat(behavior.getWeight()).isEqualTo(1.0);
        assertThat(behavior.getRating()).isNull();
        assertThat(behavior.getCreatedAt()).isNotNull();
    }
}
