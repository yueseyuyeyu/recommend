package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.ProductQuery;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.service.ProductService;
import com.deng.recommend.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品服务实现。
 *
 * <p>所有查询均通过 MyBatis-Plus Lambda 条件构造器完成，
 * 不拼接任何 SQL 片段，字段名由方法引用保证编译期安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public PageResult<ProductVO> page(ProductQuery query, long pageNum, long pageSize) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                // 前台只展示上架商品
                .eq(Product::getStatus, 1)
                .like(StringUtils.hasText(query.getKeyword()), Product::getName, query.getKeyword())
                .eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId())
                .ge(query.getMinPrice() != null, Product::getPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, Product::getPrice, query.getMaxPrice());

        applySort(wrapper, query.getSort());

        IPage<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        PageResult<Product> pageResult = PageResult.of(page);

        // 实体转 VO 后重建分页对象，保留原分页元信息
        Map<Long, String> categoryNames = loadCategoryNames();
        List<ProductVO> records = pageResult.getRecords().stream()
                .map(product -> toVO(product, categoryNames))
                .collect(Collectors.toList());
        return new PageResult<>(pageResult.getPageNum(), pageResult.getPageSize(),
                pageResult.getTotal(), records);
    }

    @Override
    public ProductVO detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (!product.isOnSale()) {
            throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF);
        }
        return toVO(product, loadCategoryNames());
    }

    @Override
    public List<ProductVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return productMapper.selectBatchIds(ids).stream()
                .map(ProductVO::from)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 类目表规模很小（两级几十条），一次查全量做内存映射，避免逐行查库。
     */
    private Map<Long, String> loadCategoryNames() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private ProductVO toVO(Product product, Map<Long, String> categoryNames) {
        ProductVO vo = ProductVO.from(product);
        vo.setCategoryName(categoryNames.get(product.getCategoryId()));
        return vo;
    }

    /**
     * 按排序参数追加排序条件。
     *
     * <p>约定：{@code sales} 销量降序、{@code price_asc} 价格升序、
     * {@code price_desc} 价格降序；不传或 {@code default} 时按销量降序，
     * 其余无法识别的值按创建时间降序，保证不会出现无排序的不稳定分页。
     *
     * @param wrapper 条件构造器
     * @param sort    排序参数
     */
    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        String normalized = sort == null ? "" : sort.trim().toLowerCase();
        switch (normalized) {
            case "sales", "default", "" -> wrapper.orderByDesc(Product::getSales);
            case "price_asc" -> wrapper.orderByAsc(Product::getPrice);
            case "price_desc" -> wrapper.orderByDesc(Product::getPrice);
            default -> wrapper.orderByDesc(Product::getCreateTime);
        }
    }
}
