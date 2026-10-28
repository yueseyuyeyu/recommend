package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.dto.ReviewRequest;
import com.deng.recommend.vo.ReviewVO;

/**
 * 商品评价服务。
 */
public interface ReviewService {

    /**
     * 提交商品评价。
     *
     * <p>依赖 {@code uk_user_product} 唯一约束保证「一人一商品限评一次」，
     * 写入前先做一次显式查重，把数据库约束异常转换为可读的业务提示。
     *
     * @param userId  当前登录用户 ID
     * @param request 评价请求
     */
    void submit(Long userId, ReviewRequest request);

    /**
     * 分页查询某商品的评价。
     *
     * @param productId 商品 ID
     * @param pageNum   页码，从 1 开始
     * @param pageSize  每页条数
     * @return 分页评价视图
     */
    PageResult<ReviewVO> pageByProduct(Long productId, long pageNum, long pageSize);
}
