package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.ReviewRequest;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.Review;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.ReviewMapper;
import com.deng.recommend.service.ReviewService;
import com.deng.recommend.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品评价服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, ReviewRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 对应 uk_user_product 唯一索引，提前查重以给出友好提示
        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getProductId, request.getProductId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "您已评价过该商品");
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setProductId(request.getProductId());
        review.setOrderNo(request.getOrderNo());
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        reviewMapper.insert(review);

        log.info("提交评价成功: userId={}, productId={}, rating={}", userId, request.getProductId(), request.getRating());
    }

    @Override
    public PageResult<ReviewVO> pageByProduct(Long productId, long pageNum, long pageSize) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .orderByDesc(Review::getCreateTime)
                .orderByDesc(Review::getId);

        PageResult<Review> pageResult = PageResult.of(
                reviewMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));

        List<ReviewVO> records = pageResult.getRecords().stream()
                .map(ReviewVO::from)
                .collect(Collectors.toList());
        return new PageResult<>(pageResult.getPageNum(), pageResult.getPageSize(),
                pageResult.getTotal(), records);
    }
}
