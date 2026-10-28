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

class UserCFStrategyTest {

    private UserCFStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new UserCFStrategy(new RecommendProperties());
    }

    private static BehaviorRecord record(long userId, long productId, double weight) {
        return new BehaviorRecord(userId, productId, weight);
    }

    private static RecommendRequest request(long userId) {
        return RecommendRequest.builder().userId(userId).topN(10).build();
    }

    private static UserItemMatrix matrix(BehaviorRecord... records) {
        return UserItemMatrix.build(Arrays.asList(records));
    }

    private static List<Long> itemIds(List<ScoredItem> result) {
        return result.stream().map(ScoredItem::getItemId).toList();
    }

    @Test
    void recommend_noHistory_returnsEmpty() {
        UserItemMatrix matrix = matrix(record(1L, 1L, 1.0));

        assertThat(strategy.recommend(matrix, request(99L))).isEmpty();
    }

    @Test
    void recommend_nullUserId_returnsEmpty() {
        UserItemMatrix matrix = matrix(record(1L, 1L, 1.0));

        assertThat(strategy.recommend(matrix, RecommendRequest.builder().topN(10).build())).isEmpty();
    }

    @Test
    void recommend_excludesInteractedItems() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0), record(1L, 2L, 1.0),
                record(2L, 1L, 1.0), record(2L, 2L, 1.0), record(2L, 3L, 1.0));

        List<ScoredItem> result = strategy.recommend(matrix, request(1L));

        assertThat(itemIds(result)).containsExactly(3L);
    }

    @Test
    void recommend_sortsByScoreDesc() {
        // u2 与 u1 相似度约为 0.1387（p1 共现），u3 与 u1 相似度为 0.5（p2 共现）
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0), record(1L, 2L, 1.0),
                record(2L, 1L, 1.0), record(2L, 3L, 5.0),
                record(3L, 2L, 1.0), record(3L, 4L, 1.0));

        List<ScoredItem> result = strategy.recommend(matrix, request(1L));

        assertThat(itemIds(result)).containsExactly(3L, 4L);
        assertThat(result.get(0).getScore()).isGreaterThan(result.get(1).getScore());
        assertThat(result.get(0).getScore()).isGreaterThan(0.6);
        assertThat(result.get(1).getScore()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void recommend_respectsNeighborK() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0), record(1L, 2L, 1.0),
                record(2L, 1L, 1.0), record(2L, 3L, 5.0),
                record(3L, 2L, 1.0), record(3L, 4L, 1.0));
        RecommendRequest request = RecommendRequest.builder()
                .userId(1L).topN(10).neighborK(1).build();

        List<ScoredItem> result = strategy.recommend(matrix, request);

        // 只保留相似度最高的邻居 u3，其候选物品为 p4
        assertThat(itemIds(result)).containsExactly(4L);
    }

    @Test
    void recommend_excludeItemIds_filtered() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0), record(1L, 2L, 1.0),
                record(2L, 1L, 1.0), record(2L, 3L, 5.0),
                record(3L, 2L, 1.0), record(3L, 4L, 1.0));
        RecommendRequest request = RecommendRequest.builder()
                .userId(1L).topN(10).excludeItemIds(List.of(3L)).build();

        assertThat(itemIds(strategy.recommend(matrix, request))).containsExactly(4L);
    }

    @Test
    void recommend_noNeighbors_returnsEmpty() {
        UserItemMatrix matrix = matrix(
                record(1L, 1L, 1.0),
                record(2L, 2L, 1.0));

        assertThat(strategy.recommend(matrix, request(1L))).isEmpty();
    }
}
