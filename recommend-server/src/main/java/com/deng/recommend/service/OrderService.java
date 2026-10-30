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

    /**
     * 支付订单：把待支付订单流转为已支付。
     *
     * <p>本项目不接真实支付渠道，这是一次受控的状态流转：
     * 只有 {@code PENDING} 状态的订单可以支付，成功后记录支付方式与支付时间。
     * 重复支付会被状态校验拦下，不需要额外的幂等键。
     *
     * @param userId  当前用户
     * @param orderNo 订单号
     * @param payType 支付方式码：1-支付宝，2-微信支付，3-银行卡
     */
    void pay(Long userId, String orderNo, Integer payType);
}
