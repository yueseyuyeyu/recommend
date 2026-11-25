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

    /** 随机基线的固定种子：保证同一份数据每次评测结果一致，可对比 */
    private static final long RANDOM_SEED = 20261125L;

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
        return evaluateInternal(UserItemMatrix.build(trainRecords), testRecords, recommender, topN);
    }

    /**
     * 执行评测，并与「纯热门」「随机」两条基线对比。
     *
     * <p>只有主结果时无法判断算法好坏——2% 的准确率到底是优秀还是不及格？
     * 有了基线才知道：推荐算法至少要比「谁热推谁」强，否则等于没做个性化。
     *
     * @param trainRecords 训练集行为
     * @param testRecords  测试集行为
     * @param recommender  待评测的推荐器
     * @param topN         每个用户推荐的物品数量
     * @return 主结果，同时携带两条基线与相对提升
     */
    public static EvaluationResult evaluateWithBaselines(List<BehaviorRecord> trainRecords,
                                                        List<BehaviorRecord> testRecords,
                                                        Recommender recommender,
                                                        int topN) {
        UserItemMatrix trainMatrix = UserItemMatrix.build(trainRecords);
        EvaluationResult main = evaluateInternal(trainMatrix, testRecords, recommender, topN);

        List<Long> ranked = popularityRanked(trainMatrix);
        EvaluationResult popularity = evaluateInternal(trainMatrix, testRecords,
                (userId, n) -> popularityTopN(ranked, trainMatrix, userId, n), topN);
        EvaluationResult random = evaluateInternal(trainMatrix, testRecords,
                randomRecommender(trainMatrix, RANDOM_SEED), topN);

        main.setPopularityBaseline(popularity);
        main.setRandomBaseline(random);
        main.setPrecisionDeltaVsPopularity(main.getPrecision() - popularity.getPrecision());
        main.setRecallDeltaVsPopularity(main.getRecall() - popularity.getRecall());
        main.setPrecisionLiftVsPopularity(relativeLift(main.getPrecision(), popularity.getPrecision()));
        main.setRecallLiftVsPopularity(relativeLift(main.getRecall(), popularity.getRecall()));
        return main;
    }

    /**
     * 相对提升：(本算法 - 基线) / 基线。
     *
     * <p>基线为 0 时返回 {@code null} 而不是 0——「基线为 0」意味着相对提升无穷大，
     * 显示成 0% 会被读反成「算法毫无提升」。此时看绝对差值更有意义。
     */
    private static Double relativeLift(double actual, double baseline) {
        if (baseline <= 0.0) {
            return null;
        }
        return (actual - baseline) / baseline;
    }

    /** 按训练集热度降序排列的物品，热门基线只算一次 */
    private static List<Long> popularityRanked(UserItemMatrix matrix) {
        return matrix.allItemIds().stream()
                .sorted((a, b) -> Double.compare(matrix.getPopularity(b), matrix.getPopularity(a)))
                .collect(Collectors.toList());
    }

    /** 热门基线：取热度最高的 N 个，跳过用户已在训练集交互过的物品 */
    private static List<ScoredItem> popularityTopN(List<Long> ranked,
                                                   UserItemMatrix matrix,
                                                   Long userId,
                                                   int topN) {
        List<ScoredItem> result = new ArrayList<>(topN);
        for (Long itemId : ranked) {
            if (result.size() >= topN) {
                break;
            }
            if (matrix.hasInteraction(userId, itemId)) {
                continue;
            }
            result.add(new ScoredItem(itemId, matrix.getPopularity(itemId), "热门基线"));
        }
        return result;
    }

    /**
     * 随机基线：从全库随机抽 N 个，跳过用户已交互物品。
     *
     * <p>种子按用户派生，保证同一用户每次抽到同一批商品——
     * 否则基线本身会抖动，没法作为对照。
     */
    private static Recommender randomRecommender(UserItemMatrix matrix, long seed) {
        List<Long> allItems = matrix.allItemIds().stream().sorted().collect(Collectors.toList());
        return (userId, topN) -> {
            List<Long> candidates = new ArrayList<>(allItems.size());
            for (Long itemId : allItems) {
                if (!matrix.hasInteraction(userId, itemId)) {
                    candidates.add(itemId);
                }
            }
            java.util.Random random = new java.util.Random(seed ^ userId.hashCode());
            java.util.Collections.shuffle(candidates, random);

            List<ScoredItem> result = new ArrayList<>(Math.min(topN, candidates.size()));
            for (int i = 0; i < topN && i < candidates.size(); i++) {
                result.add(new ScoredItem(candidates.get(i), 0.0, "随机基线"));
            }
            return result;
        };
    }

    /**
     * 单次评测的公共实现：把一段时间切分好的训练矩阵、测试集与推荐器跑出指标。
     *
     * <p>主算法与两条基线共用本方法，保证口径一致——否则基线只是「看起来」可比。
     */
    private static EvaluationResult evaluateInternal(UserItemMatrix trainMatrix,
                                                     List<BehaviorRecord> testRecords,
                                                     Recommender recommender,
                                                     int topN) {
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

        /** 纯热门基线（同样排除用户已交互物品） */
        private EvaluationResult popularityBaseline;

        /** 随机基线（种子固定，可复现） */
        private EvaluationResult randomBaseline;

        /** 相对热门基线的 Precision 提升，0.15 表示高 15%；基线为 0 时为 null */
        private Double precisionLiftVsPopularity;

        /** 相对热门基线的 Recall 提升；基线为 0 时为 null */
        private Double recallLiftVsPopularity;

        /** 相对热门基线的 Precision 绝对差值（百分点，0.01 表示高 1 个百分点） */
        private double precisionDeltaVsPopularity;

        /** 相对热门基线的 Recall 绝对差值 */
        private double recallDeltaVsPopularity;

        @Override
        public String toString() {
            return String.format(
                    "TopN=%d, 用户数=%d, Precision=%.4f, Recall=%.4f, F1=%.4f, Coverage=%.4f (%d/%d), AvgPopularity=%.4f",
                    topN, evaluatedUserCount, precision, recall, f1,
                    coverage, recommendedItemCount, totalItemCount, avgPopularity);
        }
    }
}
