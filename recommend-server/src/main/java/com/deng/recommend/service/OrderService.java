package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.dto.OrderCreateRequest;
import com.deng.recommend.vo.OrderStatusLogVO;
import com.deng.recommend.vo.OrderVO;

import java.util.List;

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

    /**
     * 用户确认收货：已发货 → 已完成。
     *
     * <p>只允许从「已发货」流转，待支付/已支付/已完成都无法确认收货，
     * 避免用户跳过发货环节直接把订单标记完成。
     *
     * @param userId  当前用户
     * @param orderNo 订单号
     */
    void confirmReceipt(Long userId, String orderNo);

    /**
     * 自动取消超时未支付的订单。
     *
     * <p>库存回滚复用 {@link #cancel} 的那套逻辑，保证「用户取消」与
     * 「系统超时取消」的结果完全一致。
     *
     * @param timeoutMinutes 未支付存活时长（分钟），小于等于 0 表示不处理
     * @return 本轮实际取消的订单数
     */
    int cancelExpiredOrders(int timeoutMinutes);

    /**
     * 查询订单的状态变更历史，仅限订单本人。
     *
     * @param userId  当前用户
     * @param orderNo 订单号
     * @return 按发生时间正序的变更记录
     */
    List<OrderStatusLogVO> logs(Long userId, String orderNo);

    /**
     * 管理员取消订单。
     *
     * <p>与买家取消共用同一套库存回滚逻辑，区别只在于日志里记录的操作者是 ADMIN。
     *
     * @param orderNo 订单号
     */
    void cancelByAdmin(String orderNo);

    /**
     * 自动确认超时未收货的订单。
     *
     * <p>买家一直不点确认收货，订单会永久停在「已发货」，交易无法完结。
     * 发货满指定天数后由本方法自动置为「已完成」。
     *
     * @param days 发货后多少天自动确认，小于等于 0 表示不处理
     * @return 本轮确认的订单数
     */
    int confirmExpiredReceipts(int days);
}
