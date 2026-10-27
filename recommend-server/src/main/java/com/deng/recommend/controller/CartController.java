package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.dto.CartAddRequest;
import com.deng.recommend.dto.CartUpdateRequest;
import com.deng.recommend.service.CartService;
import com.deng.recommend.vo.CartVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车接口。
 *
 * <p>用户 ID 一律从登录态获取，不接受请求参数，从入口处杜绝越权。
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 查询购物车列表。
     *
     * @return 购物车条目列表
     */
    @GetMapping
    public Result<List<CartVO>> list() {
        return Result.success(cartService.list(SecurityUtils.currentUserId()));
    }

    /**
     * 加入购物车。
     *
     * @param request 加购请求
     * @return 空数据
     */
    @PostMapping
    public Result<Void> add(@Valid @RequestBody CartAddRequest request) {
        cartService.add(SecurityUtils.currentUserId(), request);
        return Result.success();
    }

    /**
     * 修改购物车条目。
     *
     * @param cartId  购物车条目 ID
     * @param request 修改请求
     * @return 空数据
     */
    @PutMapping("/{cartId}")
    public Result<Void> update(@PathVariable Long cartId, @Valid @RequestBody CartUpdateRequest request) {
        cartService.update(SecurityUtils.currentUserId(), cartId, request);
        return Result.success();
    }

    /**
     * 删除购物车条目。
     *
     * @param cartId 购物车条目 ID
     * @return 空数据
     */
    @DeleteMapping("/{cartId}")
    public Result<Void> remove(@PathVariable Long cartId) {
        cartService.remove(SecurityUtils.currentUserId(), cartId);
        return Result.success();
    }

    /**
     * 清空购物车。
     *
     * @return 空数据
     */
    @DeleteMapping
    public Result<Void> clear() {
        cartService.clear(SecurityUtils.currentUserId());
        return Result.success();
    }

    /**
     * 已勾选商品合计金额。
     *
     * @return 合计金额
     */
    @GetMapping("/total")
    public Result<BigDecimal> total() {
        return Result.success(cartService.totalChecked(SecurityUtils.currentUserId()));
    }
}
