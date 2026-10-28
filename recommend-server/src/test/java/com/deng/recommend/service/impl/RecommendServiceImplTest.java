package com.deng.recommend.service.impl;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.recommend.RecommendEngine;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import com.deng.recommend.service.BehaviorService;
import com.deng.recommend.support.MybatisLambdaTestSupport;
import com.deng.recommend.vo.RecommendVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendServiceImplTest {

    @Mock
    private RecommendEngine recommendEngine;

    @Mock
    private BehaviorService behaviorService;

    @Mock
    private BehaviorMapper behaviorMapper;

    @Mock
    private ProductMapper productMapper;

    private RecommendProperties properties;
    private RecommendServiceImpl recommendService;

    @BeforeAll
    static void initTableInfo() {
        MybatisLambdaTestSupport.init();
    }

    @BeforeEach
    void setUp() {
        properties = new RecommendProperties();
        recommendService = new RecommendServiceImpl(
                recommendEngine, behaviorService, behaviorMapper, productMapper, properties);
    }

    private static Product product(long id, int status) {
        Product product = new Product();
        product.setId(id);
        product.setName("product-" + id);
        product.setPrice(BigDecimal.TEN);
        product.setStock(100);
        product.setStatus(status);
        return product;
    }

    @Test
    void recommendForUser_nonPositiveTopN_usesDefault() {
        when(behaviorService.buildMatrix(any())).thenReturn(UserItemMatrix.build(null));
        when(recommendEngine.recommendForUser(any(), anyLong(), anyInt(), anyList())).thenReturn(List.of());

        recommendService.recommendForUser(1L, 0);

        verify(recommendEngine).recommendForUser(any(), eq(1L), eq(properties.getDefaultTopN()), anyList());
    }

    @Test
    void recommendForUser_skipsOffShelfProduct() {
        when(behaviorService.buildMatrix(any())).thenReturn(UserItemMatrix.build(null));
        when(recommendEngine.recommendForUser(any(), anyLong(), anyInt(), anyList()))
                .thenReturn(List.of(new ScoredItem(1L, 5.0, "r"), new ScoredItem(2L, 4.0, "r")));
        when(productMapper.selectBatchIds(any()))
                .thenReturn(List.of(product(1L, 0), product(2L, 1)));

        List<RecommendVO> result = recommendService.recommendForUser(1L, 10);

        assertThat(result).extracting(RecommendVO::getId).containsExactly(2L);
    }

    @Test
    void recommendForUser_keepsScoreOrder() {
        when(behaviorService.buildMatrix(any())).thenReturn(UserItemMatrix.build(null));
        when(recommendEngine.recommendForUser(any(), anyLong(), anyInt(), anyList()))
                .thenReturn(List.of(new ScoredItem(2L, 0.9, "r"), new ScoredItem(1L, 0.8, "r")));
        when(productMapper.selectBatchIds(any()))
                .thenReturn(List.of(product(1L, 1), product(2L, 1)));

        List<RecommendVO> result = recommendService.recommendForUser(1L, 10);

        assertThat(result).extracting(RecommendVO::getId).containsExactly(2L, 1L);
        assertThat(result).extracting(RecommendVO::getScore).containsExactly(0.9, 0.8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recommendSimilar_excludesTargetItemAndOffShelf() {
        when(behaviorService.buildMatrix(any())).thenReturn(UserItemMatrix.build(null));
        when(recommendEngine.recommendSimilar(any(), anyLong(), anyInt(), anyList())).thenReturn(List.of());

        recommendService.recommendSimilar(100L, 5);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(recommendEngine).recommendSimilar(any(), eq(100L), eq(5), captor.capture());
        assertThat(captor.getValue()).contains(100L);
    }
}
