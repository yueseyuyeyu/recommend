package com.deng.recommend.recommend;

import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;

import java.util.List;

/**
 * 推荐策略接口。
 *
 * <p>采用策略模式封装各类推荐算法，新增算法（如内容推荐、矩阵分解）
 * 只需新增实现类并在 {@link RecommendEngine} 中注册，无需改动上层调用代码。
 */
public interface RecommendStrategy {

    /**
     * 策略名称，用于日志、推荐理由与效果归因。
     */
    String getName();

    /**
     * 执行推荐。
     *
     * <p>实现必须是<b>纯函数</b>：给定相同矩阵与请求，返回相同结果，
     * 不持有可变状态，以便单元测试与结果复现。
     *
     * @param matrix  用户-物品行为矩阵
     * @param request 推荐请求
     * @return 按推荐分降序排列的结果列表；无结果时返回空列表，绝不返回 null
     */
    List<ScoredItem> recommend(UserItemMatrix matrix, RecommendRequest request);
}
