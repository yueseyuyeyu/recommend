package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.BehaviorRecord;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendEngineTest {

    @Mock
    private UserCFStrategy userCFStrategy;

    @Mock
    private ItemCFStrategy itemCFStrategy;

    @Mock
    private HybridStrategy hybridStrategy;

    private RecommendProperties properties;
    private RecommendEngine engine;

    @BeforeEach
    void setUp() {
        properties = new RecommendProperties();
        engine = new RecommendEngine(
                userCFStrategy, itemCFStrategy,
                new HotFallbackStrategy(properties), hybridStrategy, properties);
    }

    private static BehaviorRecord record(long userId, long productId, double weight) {
        return new BehaviorRecord(userId, productId, weight);
    }

    private static UserItemMatrix hotMatrix() {
        return UserItemMatrix.build(Arrays.asList(
                record(1L, 1L, 10.0),
                record(1L, 2L, 5.0),
                record(1L, 3L, 2.0)));
    }

    private static List<Long> itemIds(List<ScoredItem> result) {
        return result.stream().map(ScoredItem::getItemId).toList();
    }

    @Test
    void recommendForUser_insufficient_fillsWithHot() {
        when(userCFStrategy.recommend(any(), any()))
                .thenReturn(List.of(new ScoredItem(9L, 100.0)));

        List<ScoredItem> result = engine.recommendForUser(hotMatrix(), 1L, 3, null);

        // 协同过滤结果不足 3 个，用热门商品补足，且不重复
        assertThat(itemIds(result)).containsExactly(9L, 1L, 2L);
        assertThat(result).hasSize(3);
    }

    @Test
    void recommendForUser_strategyThrows_degradesToHot() {
        when(userCFStrategy.recommend(any(), any()))
                .thenThrow(new IllegalStateException("algorithm failure"));

        List<ScoredItem> result = engine.recommendForUser(hotMatrix(), 1L, 3, null);

        assertThat(itemIds(result)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void recommendHot_doesNotSupplement() {
        HotFallbackStrategy hotMock = mock(HotFallbackStrategy.class);
        when(hotMock.recommend(any(), any())).thenReturn(List.of(new ScoredItem(7L, 1.0)));
        RecommendEngine engineWithMock = new RecommendEngine(
                userCFStrategy, itemCFStrategy, hotMock, hybridStrategy, properties);

        List<ScoredItem> result = engineWithMock.recommendHot(hotMatrix(), 3, null);

        assertThat(itemIds(result)).containsExactly(7L);
        verify(hotMock).recommend(any(), any());
    }

    @Test
    void supplement_noDuplicateItems() {
        when(userCFStrategy.recommend(any(), any()))
                .thenReturn(List.of(new ScoredItem(1L, 100.0)));

        List<ScoredItem> result = engine.recommendForUser(hotMatrix(), 1L, 3, null);

        assertThat(itemIds(result)).doesNotHaveDuplicates();
        assertThat(itemIds(result)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void recommendForUser_excludeIds_keptInSupplement() {
        when(userCFStrategy.recommend(any(), any())).thenReturn(List.of());

        List<ScoredItem> result = engine.recommendForUser(hotMatrix(), 1L, 2, List.of(1L, 2L));

        assertThat(itemIds(result)).containsExactly(3L);
    }
}
