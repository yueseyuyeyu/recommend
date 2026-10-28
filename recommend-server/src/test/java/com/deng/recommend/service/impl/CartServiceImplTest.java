package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.CartAddRequest;
import com.deng.recommend.dto.CartUpdateRequest;
import com.deng.recommend.entity.Cart;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.CartMapper;
import com.deng.recommend.mapper.ProductMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartMapper cartMapper;

    @Mock
    private ProductMapper productMapper;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartMapper, productMapper);
    }

    private static BusinessException catchBusinessException(ThrowingCallable callable) {
        Throwable throwable = catchThrowable(callable);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }

    private static Product product(int stock) {
        Product product = new Product();
        product.setId(100L);
        product.setName("product");
        product.setPrice(BigDecimal.TEN);
        product.setStock(stock);
        product.setStatus(1);
        return product;
    }

    private static Cart cart(long userId, long cartId, int quantity) {
        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUserId(userId);
        cart.setProductId(100L);
        cart.setQuantity(quantity);
        cart.setChecked(1);
        return cart;
    }

    private static CartAddRequest addRequest(int quantity) {
        CartAddRequest request = new CartAddRequest();
        request.setProductId(100L);
        request.setQuantity(quantity);
        return request;
    }

    private static CartUpdateRequest updateRequest(int quantity) {
        CartUpdateRequest request = new CartUpdateRequest();
        request.setQuantity(quantity);
        return request;
    }

    @Test
    void add_existingItem_increasesQuantity() {
        when(productMapper.selectById(100L)).thenReturn(product(10));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(cart(1L, 500L, 2));
        when(cartMapper.updateById(any(Cart.class))).thenReturn(1);

        cartService.add(1L, addRequest(3));

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(500L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        verify(cartMapper, never()).insert(any(Cart.class));
    }

    @Test
    void add_existingItemExceedsStock_throwsStockNotEnough() {
        when(productMapper.selectById(100L)).thenReturn(product(10));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(cart(1L, 500L, 9));

        BusinessException ex = catchBusinessException(() -> cartService.add(1L, addRequest(3)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.STOCK_NOT_ENOUGH.getCode());
        verify(cartMapper, never()).updateById(any(Cart.class));
        verify(cartMapper, never()).insert(any(Cart.class));
    }

    @Test
    void add_newItem_insertsCart() {
        when(productMapper.selectById(100L)).thenReturn(product(10));
        when(cartMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(cartMapper.insert(any(Cart.class))).thenReturn(1);

        cartService.add(1L, addRequest(2));

        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getProductId()).isEqualTo(100L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
        assertThat(captor.getValue().getChecked()).isEqualTo(1);
    }

    @Test
    void update_otherUsersCart_throwsForbidden() {
        when(cartMapper.selectById(500L)).thenReturn(cart(2L, 500L, 2));

        BusinessException ex = catchBusinessException(
                () -> cartService.update(1L, 500L, updateRequest(3)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(cartMapper, never()).updateById(any(Cart.class));
    }

    @Test
    void remove_otherUsersCart_throwsForbidden() {
        when(cartMapper.selectById(500L)).thenReturn(cart(2L, 500L, 2));

        BusinessException ex = catchBusinessException(() -> cartService.remove(1L, 500L));

        assertThat(ex.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    void update_missingCart_throwsForbidden() {
        when(cartMapper.selectById(500L)).thenReturn(null);

        BusinessException ex = catchBusinessException(
                () -> cartService.update(1L, 500L, updateRequest(3)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
    }
}
