package com.deng.recommend.recommend.evaluator;

import com.deng.recommend.recommend.model.BehaviorRecord;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐算法离线评测器。
 *
 * <p>采用<b>留出法</b>（Hold-out）：按时间或随机将行为数据划分为训练集与测试集，
 * 用训练集生成推荐，用测试集检验命中情况，输出以下指标：
 *
 * <ul>
 *   <li><b>准确率 Precision</b>：推荐列表中有多少比例被用户真实行为命中</li>
 *   <li><b>召回率 Recall</b>：用户真实感兴趣的物品中有多少比例被推荐出来</li>
 *   <li><b>F1</b>：准确率与召回率的调和平均</li>
 *   <li><b>覆盖率 Coverage</b>：推荐结果覆盖了多少比例的物品，衡量长尾挖掘能力</li>
 *   <li><b>平均流行度 AvgPopularity</b>：推荐物品的平均热度（取对数），越小说明越能挖掘冷门好物</li>
 * </ul>
 */
public final class RecommendEvaluator {

    private RecommendEvaluator() {
    }

    /**
     * 推荐器函数式接口：给定用户与推荐数量，返回推荐结果。
     */
    @FunctionalInterface
    public interface Recommender {
        List<ScoredItem> recommend(Long userId, int topN);
    }

    /**
     * 执行评测。
     *
     * @param trainRecords 训练集行为
     * @param testRecords  测试集行为
     * @param recommender  待评测的推荐器
     * @param topN         每个用户推荐的物品数量
     * @return 评测结果
     */
    public static EvaluationResult evaluate(List<BehaviorRecord> trainRecords,
                                            List<BehaviorRecord> testRecords,
                                            Recommender recommender,
                                            int topN) {
        UserItemMatrix trainMatrix = UserItemMatrix.build(trainRecords);

        // 测试集中每个用户的真实正样本
        Map<Long, Set<Long>> testPositives = new HashMap<>();
        for (BehaviorRecord record : testRecords) {
            if (record.getUserId() == null || record.getProductId() == null) {
                continue;
            }
            testPositives
                    .computeIfAbsent(record.getUserId(), k -> new HashSet<>())
                    .add(record.getProductId());
        }

        // 物品全集：训练集与测试集的并集，作为覆盖率的分母
        Set<Long> allItems = new HashSet<>(trainMatrix.allItemIds());
        testPositives.values().forEach(allItems::addAll);

        double precisionSum = 0.0;
        double recallSum = 0.0;
        double popularitySum = 0.0;
        long recommendedCount = 0L;
        int evaluatedUserCount = 0;
        Set<Long> recommendedUnion = new HashSet<>();

        for (Map.Entry<Long, Set<Long>> entry : testPositives.entrySet()) {
            Long userId = entry.getKey();
            Set<Long> positives = entry.getValue();
            if (positives.isEmpty()) {
                continue;
            }

            List<ScoredItem> recommendations = recommender.recommend(userId, topN);
            if (recommendations == null || recommendations.isEmpty()) {
                continue;
            }

            evaluatedUserCount++;
            Set<Long> recommendedIds = recommendations.stream()
                    .map(ScoredItem::getItemId)
                    .collect(Collectors.toSet());
            recommendedUnion.addAll(recommendedIds);

            long hit = recommendedIds.stream().filter(positives::contains).count();
            precisionSum += hit / (double) recommendedIds.size();
            recallSum += hit / (double) positives.size();

            for (Long itemId : recommendedIds) {
                popularitySum += Math.log1p(trainMatrix.getPopularity(itemId));
                recommendedCount++;
            }
        }

        EvaluationResult result = new EvaluationResult();
        result.setTopN(topN);
        result.setEvaluatedUserCount(evaluatedUserCount);

        if (evaluatedUserCount > 0) {
            double precision = precisionSum / evaluatedUserCount;
            double recall = recallSum / evaluatedUserCount;
            result.setPrecision(precision);
            result.setRecall(recall);
            result.setF1(precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall));
        }
        if (!allItems.isEmpty()) {
            result.setCoverage(recommendedUnion.size() / (double) allItems.size());
            result.setRecommendedItemCount(recommendedUnion.size());
            result.setTotalItemCount(allItems.size());
        }
        if (recommendedCount > 0) {
            result.setAvgPopularity(popularitySum / recommendedCount);
        }
        return result;
    }

    /**
     * 按时间切分行为数据为训练集与测试集。
     *
     * @param records      全量行为，按时间升序传入
     * @param trainRatio   训练集占比，取值 (0,1)
     * @return 长度为 2 的数组，[0] 为训练集，[1] 为测试集
     */
    public static List<BehaviorRecord>[] splitByTime(List<BehaviorRecord> records, double trainRatio) {
        List<BehaviorRecord> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> {
            if (a.getOccurredAt() == null && b.getOccurredAt() == null) {
                return 0;
            }
            if (a.getOccurredAt() == null) {
                return -1;
            }
            if (b.getOccurredAt() == null) {
                return 1;
            }
            return a.getOccurredAt().compareTo(b.getOccurredAt());
        });

        int trainSize = (int) Math.floor(sorted.size() * trainRatio);
        List<BehaviorRecord> train = new ArrayList<>(sorted.subList(0, trainSize));
        List<BehaviorRecord> test = new ArrayList<>(sorted.subList(trainSize, sorted.size()));

        @SuppressWarnings("unchecked")
        List<BehaviorRecord>[] result = new List[]{train, test};
        return result;
    }

    /**
     * 评测结果。
     */
    @Data
    public static class EvaluationResult {

        /** 每个用户的推荐数量 */
        private int topN;

        /** 参与评测的用户数（有推荐结果的用户） */
        private int evaluatedUserCount;

        /** 准确率 */
        private double precision;

        /** 召回率 */
        private double recall;

        /** F1 值 */
        private double f1;

        /** 覆盖率 */
        private double coverage;

        /** 被推荐过的物品数量 */
        private int recommendedItemCount;

        /** 物品总数 */
        private int totalItemCount;

        /** 推荐物品的平均流行度（自然对数） */
        private double avgPopularity;

        @Override
        public String toString() {
            return String.format(
                    "TopN=%d, 用户数=%d, Precision=%.4f, Recall=%.4f, F1=%.4f, Coverage=%.4f (%d/%d), AvgPopularity=%.4f",
                    topN, evaluatedUserCount, precision, recall, f1,
                    coverage, recommendedItemCount, totalItemCount, avgPopularity);
        }
    }
}
