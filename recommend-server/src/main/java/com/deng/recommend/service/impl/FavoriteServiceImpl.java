package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.entity.Favorite;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.FavoriteMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.service.FavoriteService;
import com.deng.recommend.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品收藏服务实现。
 *
 * <p><b>幂等设计</b>：收藏与取消收藏都不报"已收藏/未收藏"错误，
 * 因为前端爱心图标是开关语义——重复点击不应弹出错误提示。
 * 并发下可能同时插入两条，靠唯一键 {@code uk_user_product} 兜底，
 * 捕获 {@link DuplicateKeyException} 后视为成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    @Override
    public void add(Long userId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (exists(userId, productId)) {
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 并发重复收藏，唯一键已拦截，按成功处理
            log.debug("重复收藏被唯一键拦截: userId={}, productId={}", userId, productId);
        }
    }

    @Override
    public void remove(Long userId, Long productId) {
        // 强制带上 userId，避免删掉别人的收藏
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
    }

    @Override
    public PageResult<FavoriteVO> page(Long userId, long pageNum, long pageSize) {
        Page<Favorite> page = new Page<>(pageNum, pageSize);
        Page<Favorite> result = favoriteMapper.selectPage(page, new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime));

        List<Favorite> records = result.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), new ArrayList<>());
        }

        // 一次性批量查商品，避免 N+1
        List<Long> productIds = new ArrayList<>(records.size());
        for (Favorite favorite : records) {
            productIds.add(favorite.getProductId());
        }
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(productIds)) {
            productMap.put(product.getId(), product);
        }

        List<FavoriteVO> vos = new ArrayList<>(records.size());
        for (Favorite favorite : records) {
            Product product = productMap.get(favorite.getProductId());
            // 商品已被删除或逻辑删除时跳过（selectBatchIds 不会返回逻辑删除的数据）
            if (product == null) {
                continue;
            }
            vos.add(FavoriteVO.of(favorite.getId(), favorite.getCreateTime(), product));
        }
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), vos);
    }

    @Override
    public Set<Long> favoriteProductIds(Long userId) {
        List<Favorite> list = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .select(Favorite::getProductId));
        Set<Long> ids = new HashSet<>(list.size());
        for (Favorite favorite : list) {
            ids.add(favorite.getProductId());
        }
        return ids;
    }

    @Override
    public boolean isFavorite(Long userId, Long productId) {
        return exists(userId, productId);
    }

    @Override
    public long count(Long userId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId));
        return count == null ? 0L : count;
    }

    @Override
    public List<Long> filterFavorited(Long userId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Favorite> list = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .in(Favorite::getProductId, productIds)
                .select(Favorite::getProductId));
        List<Long> ids = new ArrayList<>(list.size());
        for (Favorite favorite : list) {
            ids.add(favorite.getProductId());
        }
        return ids;
    }

    private boolean exists(Long userId, Long productId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        return count != null && count > 0;
    }
}
