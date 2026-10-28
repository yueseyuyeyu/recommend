package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.dto.OrderCreateRequest;
import com.deng.recommend.vo.OrderVO;

/**
 * 订单服务。
 *
 * <p>下单为事务操作：校验库存 → 落库订单与明细 → 扣减库存 → 记录购买行为 → 清理购物车，
 * 任一步失败整体回滚。
 */
public interface OrderService {

    /**
     * 由已勾选的购物车条目创建订单。
     *
     * @param userId  当前登录用户 ID
     * @param request 下单请求（收货信息）
     * @return 订单号
     */
    String create(Long userId, OrderCreateRequest request);

    /**
     * 分页查询当前用户的订单。
     *
     * @param userId   当前登录用户 ID
     * @param status   订单状态，为空表示不限
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页订单列表
     */
    PageResult<OrderVO> page(Long userId, Integer status, long pageNum, long pageSize);

    /**
     * 查询订单详情，订单必须属于当前用户。
     *
     * @param userId  当前登录用户 ID
     * @param orderNo 订单号
     * @return 订单视图
     */
    OrderVO detail(Long userId, String orderNo);

    /**
     * 取消订单并归还库存。
     *
     * @param userId  当前登录用户 ID
     * @param orderNo 订单号
     */
    void cancel(Long userId, String orderNo);
}
