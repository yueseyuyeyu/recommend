package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.BehaviorRecord;
import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCFStrategyTest {

    private ItemCFStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ItemCFStrategy(new RecommendProperties());
    }

    private static BehaviorRecord record(long userId, long productId, double weight) {
        return new BehaviorRecord(userId, productId, weight);
    }

    private static RecommendRequest request(long itemId) {
        return RecommendRequest.builder().itemId(itemId).topN(10).build();
    }

    private static UserItemMatrix matrix(BehaviorRecord... records) {
        return UserItemMatrix.build(Arrays.asList(records));
    }

    private static List<Long> itemIds(List<ScoredItem> result) {
        return result.stream().map(ScoredItem::getItemId).toList();
    }

    @Test
    void recommend_excludesSelf() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0), record(2L, 1L, 1.0),
                record(1L, 2L, 1.0), record(2L, 2L, 1.0));

        List<Long> ids = itemIds(strategy.recommend(matrix, request(1L)));

        assertThat(ids).doesNotContain(1L);
        assertThat(ids).containsExactly(2L);
    }

    @Test
    void recommend_noCooccurrence_returnsEmpty() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0),
                record(2L, 2L, 1.0));

        assertThat(strategy.recommend(matrix, request(1L))).isEmpty();
    }

    @Test
    void recommend_unknownItem_returnsEmpty() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0),
                record(2L, 2L, 1.0));

        assertThat(strategy.recommend(matrix, request(99L))).isEmpty();
    }

    @Test
    void recommend_sortsBySimilarityDesc() {
        // item2 与 item1 受众完全一致（相似度 1），item3 只被 u1 交互过（相似度 0.707）
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0), record(2L, 1L, 1.0),
                record(1L, 2L, 1.0), record(2L, 2L, 1.0),
                record(1L, 3L, 1.0));

        List<ScoredItem> result = strategy.recommend(matrix, request(1L));

        assertThat(itemIds(result)).containsExactly(2L, 3L);
        assertThat(result.get(0).getScore()).isGreaterThan(result.get(1).getScore());
        assertThat(result.get(1).getScore())
                .isCloseTo(1.0 / Math.sqrt(2), org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void recommend_excludeItemIds_filtered() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0), record(2L, 1L, 1.0),
                record(1L, 2L, 1.0), record(2L, 2L, 1.0),
                record(1L, 3L, 1.0));
        RecommendRequest request = RecommendRequest.builder()
                .itemId(1L).topN(10).excludeItemIds(List.of(2L)).build();

        assertThat(itemIds(strategy.recommend(matrix, request))).containsExactly(3L);
    }
}
