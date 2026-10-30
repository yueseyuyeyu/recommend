package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.vo.FavoriteVO;

import java.util.List;
import java.util.Set;

/**
 * 商品收藏服务。
 *
 * <p>所有方法都以 {@code userId} 为第一个参数，强制在 SQL 层拼上
 * {@code user_id = ?} 过滤条件，从设计上杜绝「传别人的 ID 就能看别人收藏」的越权问题。
 */
public interface FavoriteService {

    /**
     * 收藏商品。已收藏时幂等返回，不报错。
     *
     * @param userId    当前登录用户
     * @param productId 商品 ID
     */
    void add(Long userId, Long productId);

    /**
     * 取消收藏。未收藏时幂等返回。
     *
     * @param userId    当前登录用户
     * @param productId 商品 ID
     */
    void remove(Long userId, Long productId);

    /**
     * 分页查询我的收藏，按收藏时间倒序。
     */
    PageResult<FavoriteVO> page(Long userId, long pageNum, long pageSize);

    /**
     * 查询当前用户收藏的全部商品 ID。
     *
     * <p>用于商品列表页一次性拿到"哪些已收藏"，避免每个卡片发一次请求。
     */
    Set<Long> favoriteProductIds(Long userId);

    /**
     * 判断是否已收藏某商品。
     */
    boolean isFavorite(Long userId, Long productId);

    /**
     * 当前用户的收藏总数。
     */
    long count(Long userId);

    /**
     * 批量查询：从给定商品 ID 中筛出已被当前用户收藏的那些。
     */
    List<Long> filterFavorited(Long userId, List<Long> productIds);
}
