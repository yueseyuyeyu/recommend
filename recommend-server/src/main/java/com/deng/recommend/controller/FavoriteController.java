package com.deng.recommend.controller;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.service.FavoriteService;
import com.deng.recommend.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 商品收藏接口。
 *
 * <p>所有接口都从 {@link SecurityUtils#currentUserId()} 取当前用户，
 * <b>不接受前端传入 userId</b>——这是防越权最关键的一点。
 */
@Validated
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 收藏商品（幂等，重复收藏不报错）。
     */
    @PostMapping("/{productId}")
    public Result<Void> add(@PathVariable Long productId) {
        favoriteService.add(SecurityUtils.currentUserId(), productId);
        return Result.success(null);
    }

    /**
     * 取消收藏（幂等，未收藏时也返回成功）。
     */
    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        favoriteService.remove(SecurityUtils.currentUserId(), productId);
        return Result.success(null);
    }

    /**
     * 我的收藏，分页，按收藏时间倒序。
     */
    @GetMapping
    public Result<PageResult<FavoriteVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "12") long pageSize) {
        return Result.success(favoriteService.page(SecurityUtils.currentUserId(), pageNum, pageSize));
    }

    /**
     * 当前用户已收藏的全部商品 ID。
     *
     * <p>商品列表页进入时调一次，前端据此把对应爱心图标直接渲染成红色，
     * 避免每个卡片单独发请求。
     */
    @GetMapping("/ids")
    public Result<Set<Long>> ids() {
        return Result.success(favoriteService.favoriteProductIds(SecurityUtils.currentUserId()));
    }

    /**
     * 判断单个商品是否已收藏。
     */
    @GetMapping("/check/{productId}")
    public Result<Boolean> check(@PathVariable Long productId) {
        return Result.success(favoriteService.isFavorite(SecurityUtils.currentUserId(), productId));
    }

    /**
     * 收藏总数，用于导航栏角标。
     */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(favoriteService.count(SecurityUtils.currentUserId()));
    }

    /**
     * 批量判断：从给定商品 ID 中筛出已收藏的。
     *
     * <p>供详情页"相关推荐"等小范围列表使用，比拉全量 ids 更省。
     */
    @GetMapping("/filter")
    public Result<List<Long>> filter(@RequestParam List<Long> productIds) {
        return Result.success(favoriteService.filterFavorited(SecurityUtils.currentUserId(), productIds));
    }
}
