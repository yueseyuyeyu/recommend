package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.CartAddRequest;
import com.deng.recommend.dto.CartUpdateRequest;
import com.deng.recommend.entity.Cart;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.CartMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.service.CartService;
import com.deng.recommend.vo.CartVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 购物车服务实现。
 *
 * <p>加入购物车时实时校验商品上架状态与库存，避免无效数据进入结算流程；
 * 修改与删除均强制校验条目归属，构成越权防护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;

    @Override
    public void add(Long userId, CartAddRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (!product.isOnSale()) {
            throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF);
        }

        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, request.getProductId()));

        // 已存在则累加，需以累加后的总量校验库存
        int targetQuantity = exist == null
                ? request.getQuantity()
                : nullSafe(exist.getQuantity()) + request.getQuantity();
        if (nullSafe(product.getStock()) < targetQuantity) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        if (exist != null) {
            Cart update = new Cart();
            update.setId(exist.getId());
            update.setQuantity(targetQuantity);
            cartMapper.updateById(update);
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(request.getProductId());
            cart.setQuantity(request.getQuantity());
            cart.setChecked(1);
            cartMapper.insert(cart);
        }
        log.info("加入购物车成功, userId={}, productId={}", userId, request.getProductId());
    }

    @Override
    public List<CartVO> list(Long userId) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getId));
        if (carts.isEmpty()) {
            return Collections.emptyList();
        }

        List<CartVO> result = new ArrayList<>(carts.size());
        for (Cart cart : carts) {
            Product product = productMapper.selectById(cart.getProductId());
            BigDecimal price = product == null || product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            result.add(CartVO.builder()
                    .id(cart.getId())
                    .productId(cart.getProductId())
                    .productName(product == null ? null : product.getName())
                    .mainImage(product == null ? null : product.getMainImage())
                    .price(price)
                    .quantity(cart.getQuantity())
                    .checked(cart.getChecked())
                    .stock(product == null ? 0 : nullSafe(product.getStock()))
                    .subtotal(price.multiply(BigDecimal.valueOf(nullSafe(cart.getQuantity()))))
                    .build());
        }
        return result;
    }

    @Override
    public void update(Long userId, Long cartId, CartUpdateRequest request) {
        Cart cart = getOwnedCart(userId, cartId);

        Cart update = new Cart();
        update.setId(cart.getId());
        update.setQuantity(request.getQuantity());
        if (request.getChecked() != null) {
            update.setChecked(request.getChecked());
        }
        cartMapper.updateById(update);
    }

    @Override
    public void remove(Long userId, Long cartId) {
        // getOwnedCart 内部完成归属校验，越权访问在此被拦截
        Cart cart = getOwnedCart(userId, cartId);
        cartMapper.deleteById(cart.getId());
    }

    @Override
    public void clear(Long userId) {
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
    }

    @Override
    public BigDecimal totalChecked(Long userId) {
        List<Cart> checkedCarts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getChecked, 1));
        BigDecimal total = BigDecimal.ZERO;
        for (Cart cart : checkedCarts) {
            Product product = productMapper.selectById(cart.getProductId());
            if (product == null || product.getPrice() == null) {
                continue;
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(nullSafe(cart.getQuantity()))));
        }
        return total;
    }

    /**
     * 加载购物车条目并校验归属，非本人条目一律按无权限处理。
     */
    private Cart getOwnedCart(Long userId, Long cartId) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !Objects.equals(cart.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return cart;
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
