package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.BehaviorRecord;
import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridStrategyTest {

    private static BehaviorRecord record(long userId, long productId, double weight) {
        return new BehaviorRecord(userId, productId, weight);
    }

    private static UserItemMatrix matrix(BehaviorRecord... records) {
        return UserItemMatrix.build(Arrays.asList(records));
    }

    /**
     * u1={1,2}, u2={1,3}, u3={2,4}；item1={u1,u2}, item2={u1,u3}, item3={u2}, item4={u3}
     */
    private static UserItemMatrix sample() {
        return matrix(
                record(1L, 1L, 1.0), record(1L, 2L, 1.0),
                record(2L, 1L, 1.0), record(2L, 3L, 5.0),
                record(3L, 2L, 1.0), record(3L, 4L, 1.0));
    }

    private static HybridStrategy strategy(RecommendProperties properties) {
        return new HybridStrategy(
                new UserCFStrategy(properties), new ItemCFStrategy(properties), properties);
    }

    private static List<Long> itemIds(List<ScoredItem> result) {
        return result.stream().map(ScoredItem::getItemId).toList();
    }

    @Test
    void recommend_bothEmpty_returnsEmpty() {
        HybridStrategy hybrid = strategy(new RecommendProperties());
        RecommendRequest request = RecommendRequest.builder().topN(10).build();

        assertThat(hybrid.recommend(sample(), request)).isEmpty();
        assertThat(hybrid.recommend(new UserItemMatrix(), request)).isEmpty();
        assertThat(hybrid.recommend(null, request)).isEmpty();
    }

    @Test
    void recommend_normalizesBeforeMerge() {
        HybridStrategy hybrid = strategy(new RecommendProperties());
        RecommendRequest request = RecommendRequest.builder()
                .userId(1L).itemId(1L).topN(10).build();

        List<ScoredItem> result = hybrid.recommend(sample(), request);

        // p3 在 UserCF 与 ItemCF 中均为最高分，归一化后 1.0×0.6 + 1.0×0.4 = 1.0
        assertThat(itemIds(result)).containsExactly(3L, 4L, 2L);
        assertThat(result.get(0).getScore()).isCloseTo(
                1.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(result.get(0).getScore()).isGreaterThan(result.get(1).getScore());
        assertThat(result.get(1).getScore()).isGreaterThan(result.get(2).getScore());
    }

    @Test
    void recommend_weightZero_ignored() {
        RecommendProperties properties = new RecommendProperties();
        properties.setUserCfWeight(0.0);
        properties.setItemCfWeight(1.0);
        HybridStrategy hybrid = strategy(properties);
        RecommendRequest request = RecommendRequest.builder()
                .userId(1L).itemId(1L).topN(10).build();

        List<Long> ids = itemIds(hybrid.recommend(sample(), request));

        // UserCF 权重为 0，其独有的 p4 不再出现，仅保留 ItemCF 的 p2、p3（且按分数降序）
        assertThat(ids).containsExactly(3L, 2L);
        assertThat(ids).doesNotContain(4L);
    }
}
