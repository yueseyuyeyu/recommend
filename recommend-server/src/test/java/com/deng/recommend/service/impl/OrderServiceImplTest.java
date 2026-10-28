package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.OrderCreateRequest;
import com.deng.recommend.entity.Behavior;
import com.deng.recommend.entity.Cart;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.OrderItem;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.enums.BehaviorType;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CartMapper;
import com.deng.recommend.mapper.OrderItemMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private CartMapper cartMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private BehaviorMapper behaviorMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, orderItemMapper, cartMapper, productMapper, behaviorMapper);
    }

    private static BusinessException catchBusinessException(ThrowingCallable callable) {
        Throwable throwable = catchThrowable(callable);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }

    private static OrderCreateRequest createRequest() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setReceiverName("张三");
        request.setReceiverPhone("13800000000");
        request.setReceiverAddr("上海市");
        return request;
    }

    private static Cart cart(long id, long productId, int quantity) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(1L);
        cart.setProductId(productId);
        cart.setQuantity(quantity);
        cart.setChecked(1);
        return cart;
    }

    private static Product product(long id, BigDecimal price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName("product-" + id);
        product.setPrice(price);
        product.setStock(stock);
        product.setStatus(1);
        return product;
    }

    private static Order order(String orderNo, int status) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setUserId(1L);
        order.setStatus(status);
        return order;
    }

    private static OrderItem orderItem(long productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderNo("NO1");
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void create_emptyCart_throwsCartEmpty() {
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        BusinessException ex = catchBusinessException(() -> orderService.create(1L, createRequest()));

        assertThat(ex.getCode()).isEqualTo(ResultCode.CART_EMPTY.getCode());
        verifyNoInteractions(orderMapper, orderItemMapper, productMapper, behaviorMapper);
    }

    @Test
    void create_stockNotEnough_throwsStockNotEnough() {
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cart(500L, 100L, 3)));
        when(productMapper.selectById(100L)).thenReturn(product(100L, BigDecimal.TEN, 2));

        BusinessException ex = catchBusinessException(() -> orderService.create(1L, createRequest()));

        assertThat(ex.getCode()).isEqualTo(ResultCode.STOCK_NOT_ENOUGH.getCode());
        verifyNoInteractions(orderMapper, orderItemMapper, behaviorMapper);
    }

    @Test
    void create_stockDeductionAffectsZeroRows_throwsStockNotEnough() {
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cart(500L, 100L, 1)));
        when(productMapper.selectById(100L)).thenReturn(product(100L, BigDecimal.TEN, 10));
        when(productMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        BusinessException ex = catchBusinessException(() -> orderService.create(1L, createRequest()));

        assertThat(ex.getCode()).isEqualTo(ResultCode.STOCK_NOT_ENOUGH.getCode());
        verify(behaviorMapper, never()).insert(any(Behavior.class));
        verify(cartMapper, never()).deleteBatchIds(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_success_writesOrderItemsPurchaseAndClearsCart() {
        when(cartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cart(500L, 100L, 2)));
        when(productMapper.selectById(100L)).thenReturn(product(100L, BigDecimal.TEN, 10));
        when(productMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);
        when(behaviorMapper.insert(any(Behavior.class))).thenReturn(1);

        String orderNo = orderService.create(1L, createRequest());

        assertThat(orderNo).startsWith("NO").hasSize(22);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getUserId()).isEqualTo(1L);
        assertThat(savedOrder.getOrderNo()).isEqualTo(orderNo);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("20");
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING.getCode());
        assertThat(savedOrder.getReceiverName()).isEqualTo("张三");

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getOrderNo()).isEqualTo(orderNo);
        assertThat(savedItem.getProductId()).isEqualTo(100L);
        assertThat(savedItem.getQuantity()).isEqualTo(2);
        assertThat(savedItem.getSubtotal()).isEqualByComparingTo("20");

        ArgumentCaptor<Behavior> behaviorCaptor = ArgumentCaptor.forClass(Behavior.class);
        verify(behaviorMapper).insert(behaviorCaptor.capture());
        Behavior behavior = behaviorCaptor.getValue();
        assertThat(behavior.getUserId()).isEqualTo(1L);
        assertThat(behavior.getProductId()).isEqualTo(100L);
        assertThat(behavior.getBehaviorType()).isEqualTo(BehaviorType.PURCHASE.getCode());
        assertThat(behavior.getWeight()).isEqualTo(BehaviorType.PURCHASE.getWeight());

        ArgumentCaptor<Collection> cartIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(cartMapper).deleteBatchIds(cartIdsCaptor.capture());
        assertThat(cartIdsCaptor.getValue()).containsExactly(500L);

        // 每行商品执行两次更新：扣库存 + 加销量
        verify(productMapper, times(2)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void cancel_nonCancellableOrder_throwsOrderNotCancellable() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("NO1", OrderStatus.SHIPPED.getCode()));

        BusinessException ex = catchBusinessException(() -> orderService.cancel(1L, "NO1"));

        assertThat(ex.getCode()).isEqualTo(ResultCode.ORDER_NOT_CANCELLABLE.getCode());
        verify(orderItemMapper, never()).selectList(any(Wrapper.class));
        verify(productMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void cancel_pendingOrder_restoresStockAndUpdatesStatus() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("NO1", OrderStatus.PENDING.getCode()));
        when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orderItem(100L, 3)));
        when(productMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        orderService.cancel(1L, "NO1");

        verify(productMapper).update(isNull(), any(Wrapper.class));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
    }

    @Test
    void cancel_orderOfAnotherUser_throwsForbidden() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order("NO1", OrderStatus.PENDING.getCode()));

        BusinessException ex = catchBusinessException(() -> orderService.cancel(2L, "NO1"));

        assertThat(ex.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
    }
}
