package com.deng.recommend.controller;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.dto.OrderCreateRequest;
import com.deng.recommend.service.OrderService;
import com.deng.recommend.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 *
 * <p>订单号由服务端生成返回，查询与操作均以登录用户 ID 为过滤条件。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 提交订单。
     *
     * @param request 下单请求
     * @return 订单号
     */
    @PostMapping
    public Result<String> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success(orderService.create(SecurityUtils.currentUserId(), request));
    }

    /**
     * 分页查询我的订单。
     *
     * @param status   订单状态，可为空
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @return 分页订单列表
     */
    @GetMapping
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(orderService.page(SecurityUtils.currentUserId(), status, pageNum, pageSize));
    }

    /**
     * 查询订单详情。
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.success(orderService.detail(SecurityUtils.currentUserId(), orderNo));
    }

    /**
     * 取消订单。
     *
     * @param orderNo 订单号
     * @return 空数据
     */
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderService.cancel(SecurityUtils.currentUserId(), orderNo);
        return Result.success();
    }

    /**
     * 支付订单。
     *
     * <p>只接受待支付订单；支付方式由前端在下单时选好、这里再确认一次，
     * 避免用户在下单和支付之间改了主意却没同步到订单上。
     *
     * @param orderNo 订单号
     * @param payType 支付方式码：1-支付宝，2-微信支付，3-银行卡
     * @return 空数据
     */
    @PostMapping("/{orderNo}/pay")
    public Result<Void> pay(@PathVariable String orderNo,
                            @RequestParam Integer payType) {
        orderService.pay(SecurityUtils.currentUserId(), orderNo, payType);
        return Result.success();
    }

    /**
     * 确认收货：已发货 → 已完成。
     *
     * <p>由用户自己点，不必等后台操作。
     */
    @PostMapping("/{orderNo}/receipt")
    public Result<Void> confirmReceipt(@PathVariable String orderNo) {
        orderService.confirmReceipt(SecurityUtils.currentUserId(), orderNo);
        return Result.success();
    }
}
