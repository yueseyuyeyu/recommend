package com.deng.recommend.recommend.evaluator;

import com.deng.recommend.recommend.evaluator.RecommendEvaluator.EvaluationResult;
import com.deng.recommend.recommend.model.BehaviorRecord;
import com.deng.recommend.recommend.model.ScoredItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecommendEvaluatorTest {

    private static BehaviorRecord record(long userId, long productId, double weight) {
        return new BehaviorRecord(userId, productId, weight);
    }

    private static BehaviorRecord record(long userId, long productId, double weight, LocalDateTime time) {
        return new BehaviorRecord(userId, productId, weight, time);
    }

    @Test
    void evaluate_perfectHit_precisionAndRecallOne() {
        List<BehaviorRecord> train = List.of(record(1L, 10L, 1.0));
        List<BehaviorRecord> test = List.of(record(1L, 10L, 1.0));

        EvaluationResult result = RecommendEvaluator.evaluate(
                train, test, (userId, topN) -> List.of(new ScoredItem(10L, 1.0)), 1);

        assertThat(result.getPrecision()).isCloseTo(1.0, within(1e-9));
        assertThat(result.getRecall()).isCloseTo(1.0, within(1e-9));
        assertThat(result.getF1()).isCloseTo(1.0, within(1e-9));
        assertThat(result.getEvaluatedUserCount()).isEqualTo(1);
        assertThat(result.getCoverage()).isCloseTo(1.0, within(1e-9));
        assertThat(result.getTotalItemCount()).isEqualTo(1);
    }

    @Test
    void evaluate_noHit_returnsZero() {
        List<BehaviorRecord> train = List.of(record(2L, 20L, 1.0));
        List<BehaviorRecord> test = List.of(record(1L, 10L, 1.0));

        EvaluationResult result = RecommendEvaluator.evaluate(
                train, test, (userId, topN) -> List.of(new ScoredItem(11L, 1.0)), 1);

        assertThat(result.getPrecision()).isZero();
        assertThat(result.getRecall()).isZero();
        assertThat(result.getF1()).isZero();
        assertThat(result.getEvaluatedUserCount()).isEqualTo(1);
    }

    @Test
    void evaluate_emptyRecommendation_skipsUser() {
        List<BehaviorRecord> train = List.of(record(1L, 10L, 1.0));
        List<BehaviorRecord> test = List.of(record(1L, 10L, 1.0));

        EvaluationResult result = RecommendEvaluator.evaluate(
                train, test, (userId, topN) -> List.of(), 1);

        assertThat(result.getEvaluatedUserCount()).isZero();
        assertThat(result.getPrecision()).isZero();
        assertThat(result.getRecall()).isZero();
    }

    @Test
    void splitByTime_ratio80_20() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 0, 0);
        List<BehaviorRecord> records = new ArrayList<>();
        // 乱序传入，验证内部按时间升序排序
        for (int i = 9; i >= 0; i--) {
            records.add(record(1L, 100L + i, 1.0, base.plusMinutes(i)));
        }

        List<BehaviorRecord>[] split = RecommendEvaluator.splitByTime(records, 0.8);

        assertThat(split[0]).hasSize(8);
        assertThat(split[1]).hasSize(2);
        assertThat(split[0].get(0).getOccurredAt()).isEqualTo(base);
        assertThat(split[0].get(7).getOccurredAt()).isEqualTo(base.plusMinutes(7));
        assertThat(split[1].get(0).getOccurredAt()).isEqualTo(base.plusMinutes(8));
        assertThat(split[1].get(1).getOccurredAt()).isEqualTo(base.plusMinutes(9));
    }

    @Test
    void evaluate_coverageUsesItemUnion() {
        List<BehaviorRecord> train = Arrays.asList(record(1L, 10L, 1.0), record(2L, 20L, 1.0));
        List<BehaviorRecord> test = List.of(record(1L, 30L, 1.0));

        EvaluationResult result = RecommendEvaluator.evaluate(
                train, test,
                (userId, topN) -> List.of(new ScoredItem(10L, 1.0), new ScoredItem(20L, 1.0)), 2);

        // 分母为训练集物品 {10,20} 与测试集正样本 {30} 的并集，共 3 个
        assertThat(result.getTotalItemCount()).isEqualTo(3);
        assertThat(result.getRecommendedItemCount()).isEqualTo(2);
        assertThat(result.getCoverage()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void evaluateWithBaselines_fillsBaselinesAndComputesLift() {
        // 用户 1 在训练集看过 10、11；冷门商品 20/30/40 来自用户 2
        List<BehaviorRecord> train = new ArrayList<>();
        train.add(record(1L, 10L, 10.0));
        train.add(record(1L, 11L, 9.0));
        train.add(record(2L, 20L, 8.0));
        train.add(record(2L, 30L, 7.0));
        train.add(record(2L, 40L, 6.0));
        // 测试集里用户 1 真正感兴趣的是 20 与 40
        List<BehaviorRecord> test = List.of(record(1L, 20L, 1.0), record(1L, 40L, 1.0));

        EvaluationResult result = RecommendEvaluator.evaluateWithBaselines(
                train, test,
                (userId, topN) -> List.of(new ScoredItem(20L, 1.0), new ScoredItem(40L, 1.0)),
                2);

        assertThat(result.getPopularityBaseline()).isNotNull();
        assertThat(result.getRandomBaseline()).isNotNull();
        assertThat(result.getPrecision()).isCloseTo(1.0, within(1e-9));

        // 热门基线会推 20 与 30：20 命中、30 不命中，准确率 0.5
        assertThat(result.getPopularityBaseline().getPrecision()).isCloseTo(0.5, within(1e-9));
        // 相对提升 = (1.0 - 0.5) / 0.5 = 100%
        assertThat(result.getPrecisionLiftVsPopularity()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void evaluateWithBaselines_baselinesSkipItemsUserAlreadyTouched() {
        // 用户 1 训练集看过 10，测试集又出现 10。
        // 若基线不过滤已交互物品，就会推 10 并命中，准确率变成 1.0；
        // 正确实现应跳过 10，改推 20，因此准确率为 0。
        List<BehaviorRecord> train = new ArrayList<>();
        train.add(record(1L, 10L, 10.0));
        train.add(record(2L, 20L, 8.0));
        List<BehaviorRecord> test = List.of(record(1L, 10L, 1.0));

        EvaluationResult result = RecommendEvaluator.evaluateWithBaselines(
                train, test, (userId, topN) -> List.of(), 1);

        assertThat(result.getPopularityBaseline().getPrecision()).isZero();
        assertThat(result.getRandomBaseline().getPrecision()).isZero();
        // 基线为 0 时相对提升无意义，应为 null 而不是 0（否则会被读成「毫无提升」）
        assertThat(result.getPrecisionLiftVsPopularity()).isNull();
        // 绝对差值仍然可读
        assertThat(result.getPrecisionDeltaVsPopularity()).isZero();
    }
}
