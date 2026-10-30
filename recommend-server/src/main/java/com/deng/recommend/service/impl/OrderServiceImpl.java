package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.OrderCreateRequest;
import com.deng.recommend.entity.Behavior;
import com.deng.recommend.entity.Cart;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.OrderItem;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.enums.BehaviorType;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.entity.enums.PayType;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CartMapper;
import com.deng.recommend.mapper.OrderItemMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.service.AddressService;
import com.deng.recommend.service.OrderService;
import com.deng.recommend.vo.AddressVO;
import com.deng.recommend.vo.OrderItemVO;
import com.deng.recommend.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务实现。
 *
 * <p><b>防超卖</b>：扣减库存使用带 {@code stock >= quantity} 条件的原子 UPDATE，
 * 影响行数为 0 说明并发下已被其他订单占用，抛异常触发事务回滚，
 * 而非先查后写的非原子写法。
 *
 * <p><b>推荐闭环</b>：下单成功后写入 PURCHASE 行为，
 * 使协同过滤算法无需从订单表反推即可获得最高权重的行为数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final BehaviorMapper behaviorMapper;
    private final AddressService addressService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(Long userId, OrderCreateRequest request) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getChecked, 1));
        if (carts.isEmpty()) {
            throw new BusinessException(ResultCode.CART_EMPTY);
        }

        // 下单前统一校验商品状态与库存，并缓存商品快照用于后续落明细
        Map<Long, Product> productMap = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Cart cart : carts) {
            Product product = productMapper.selectById(cart.getProductId());
            if (product == null || !product.isOnSale()) {
                throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF);
            }
            int quantity = cart.getQuantity() == null ? 0 : cart.getQuantity();
            int stock = product.getStock() == null ? 0 : product.getStock();
            if (stock < quantity) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
            }
            productMap.put(product.getId(), product);
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        Order order = new Order();
        String orderNo = generateOrderNo();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING.getCode());
        // 支付方式在下单时选定，真正支付时才置为已支付
        if (request.getPayType() != null) {
            order.setPayType(PayType.of(request.getPayType()).getCode());
        }

        // 收货信息：优先用地址簿（服务端回查，杜绝前端伪造），否则用请求里的手填值
        ReceiverInfo receiver = resolveReceiver(userId, request);
        order.setReceiverName(receiver.name());
        order.setReceiverPhone(receiver.phone());
        order.setReceiverAddr(receiver.addr());
        orderMapper.insert(order);

        LocalDateTime now = LocalDateTime.now();
        List<Long> cartIds = new ArrayList<>(carts.size());
        for (Cart cart : carts) {
            Product product = productMap.get(cart.getProductId());
            int quantity = cart.getQuantity() == null ? 0 : cart.getQuantity();
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));

            OrderItem item = new OrderItem();
            item.setOrderNo(orderNo);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setPrice(product.getPrice());
            item.setQuantity(quantity);
            item.setSubtotal(subtotal);
            orderItemMapper.insert(item);

            // 条件扣减库存：库存不足时影响行数为 0，回滚整个下单事务
            LambdaUpdateWrapper<Product> stockWrapper = new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, product.getId())
                    .ge(Product::getStock, quantity)
                    .setSql("stock = stock - " + quantity);
            if (productMapper.update(null, stockWrapper) == 0) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
            }
            LambdaUpdateWrapper<Product> salesWrapper = new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, product.getId())
                    .setSql("sales = sales + " + quantity);
            productMapper.update(null, salesWrapper);

            Behavior behavior = new Behavior();
            behavior.setUserId(userId);
            behavior.setProductId(product.getId());
            behavior.applyType(BehaviorType.PURCHASE);
            behavior.setCreatedAt(now);
            behaviorMapper.insert(behavior);

            cartIds.add(cart.getId());
        }

        cartMapper.deleteBatchIds(cartIds);
        log.info("下单成功, userId={}, orderNo={}, totalAmount={}", userId, orderNo, totalAmount);
        return orderNo;
    }

    @Override
    public PageResult<OrderVO> page(Long userId, Integer status, long pageNum, long pageSize) {
        // userId 恒定作为过滤条件，用户只能看到自己的订单
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId);
        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<OrderVO> records = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public OrderVO detail(Long userId, String orderNo) {
        Order order = getOwnedOrder(userId, orderNo);
        return toVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        Order order = getOwnedOrder(userId, orderNo);
        if (!order.getStatusEnum().cancellable()) {
            throw new BusinessException(ResultCode.ORDER_NOT_CANCELLABLE);
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));
        for (OrderItem item : items) {
            LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, item.getProductId())
                    .setSql("stock = stock + " + item.getQuantity());
            productMapper.update(null, wrapper);
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(OrderStatus.CANCELLED.getCode());
        orderMapper.updateById(update);
        log.info("取消订单成功, userId={}, orderNo={}", userId, orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, String orderNo, Integer payType) {
        Order order = getOwnedOrder(userId, orderNo);

        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING.getCode()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL, "该订单不是待支付状态，无法支付");
        }

        PayType type = PayType.of(payType);

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(OrderStatus.PAID.getCode());
        update.setPayType(type.getCode());
        update.setPaidTime(LocalDateTime.now());
        orderMapper.updateById(update);

        log.info("订单支付成功, userId={}, orderNo={}, payType={}", userId, orderNo, type.getName());
    }

    /**
     * 生成业务订单号：{@code NO} + 时间戳(yyyyMMddHHmmss) + 6 位随机数。
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FORMATTER);
        int random = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "NO" + timestamp + random;
    }

    /**
     * 组装订单视图，包含订单明细快照。
     */
    private OrderVO toVO(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, order.getOrderNo()));
        return OrderVO.builder()
                .orderNo(order.getOrderNo())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .statusName(order.getStatusEnum().getName())
                .payType(order.getPayType())
                .payTypeName(PayType.nameOf(order.getPayType()))
                .paidTime(order.getPaidTime())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .receiverAddr(order.getReceiverAddr())
                .createTime(order.getCreateTime())
                .items(items.stream().map(OrderItemVO::from).toList())
                .build();
    }

    /**
     * 加载订单并校验归属，非本人订单一律按无权限处理。
     */
    private Order getOwnedOrder(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return order;
    }

    /**
     * 解析收货信息。
     *
     * <p>优先走地址簿：{@code addressId} 会经 {@code AddressService.getOwned} 回查，
     * 顺带校验这条地址属于当前用户——前端传什么收货信息都不作数，
     * 避免有人改请求体把货寄到别处。
     *
     * <p>没有 {@code addressId} 时退化为手填三项，三项必须齐全。
     */
    private ReceiverInfo resolveReceiver(Long userId, OrderCreateRequest request) {
        if (request.getAddressId() != null) {
            AddressVO address = addressService.getOwned(userId, request.getAddressId());
            return new ReceiverInfo(address.getReceiverName(), address.getReceiverPhone(),
                    address.getDetailAddress());
        }

        if (!StringUtils.hasText(request.getReceiverName())
                || !StringUtils.hasText(request.getReceiverPhone())
                || !StringUtils.hasText(request.getReceiverAddr())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择收货地址");
        }
        return new ReceiverInfo(request.getReceiverName().trim(),
                request.getReceiverPhone().trim(),
                request.getReceiverAddr().trim());
    }

    /** 解析后的收货信息 */
    private record ReceiverInfo(String name, String phone, String addr) {
    }
}
