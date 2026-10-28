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

class HotFallbackStrategyTest {

    private HotFallbackStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new HotFallbackStrategy(new RecommendProperties());
    }

    private static BehaviorRecord record(long userId, long productId, double weight) {
        return new BehaviorRecord(userId, productId, weight);
    }

    private static RecommendRequest request() {
        return RecommendRequest.builder().topN(10).build();
    }

    private static UserItemMatrix matrix(BehaviorRecord... records) {
        return UserItemMatrix.build(Arrays.asList(records));
    }

    @Test
    void recommend_sortsByPopularityDesc() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0),
                record(2L, 1L, 1.0),
                record(1L, 2L, 2.0),
                record(3L, 2L, 1.0));

        List<ScoredItem> result = strategy.recommend(matrix, request());

        assertThat(result).extracting(ScoredItem::getItemId).containsExactly(2L, 1L);
        assertThat(result.get(0).getScore()).isEqualTo(3.0);
        assertThat(result.get(1).getScore()).isEqualTo(2.0);
    }

    @Test
    void recommend_excludeIds_filtered() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0),
                record(2L, 1L, 1.0),
                record(1L, 2L, 2.0));
        RecommendRequest request = RecommendRequest.builder()
                .topN(10).excludeItemIds(List.of(2L)).build();

        assertThat(strategy.recommend(matrix, request))
                .extracting(ScoredItem::getItemId).containsExactly(1L);
    }

    @Test
    void recommend_emptyMatrix_returnsEmpty() {
        assertThat(strategy.recommend(null, request())).isEmpty();
        assertThat(strategy.recommend(new UserItemMatrix(), request())).isEmpty();
        assertThat(strategy.recommend(UserItemMatrix.build(null), request())).isEmpty();
    }

    @Test
    void recommend_zeroPopularityItems_excluded() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0),
                record(1L, 3L, 0.0));

        List<ScoredItem> result = strategy.recommend(matrix, request());

        assertThat(result).extracting(ScoredItem::getItemId).containsExactly(1L);
        assertThat(result).allSatisfy(item -> assertThat(item.getScore()).isGreaterThan(0.0));
    }
}
