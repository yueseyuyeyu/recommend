package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.ProductQuery;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.support.MybatisLambdaTestSupport;
import com.deng.recommend.vo.ProductVO;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 商品服务单元测试：重点验证类目名称回填与上下架过滤。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        MybatisLambdaTestSupport.init();
        productService = new ProductServiceImpl(productMapper, categoryMapper);
    }

    private static BusinessException catchBusinessException(ThrowingCallable callable) {
        Throwable throwable = catchThrowable(callable);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }

    private static Product product(int status) {
        Product product = new Product();
        product.setId(7L);
        product.setCategoryId(11L);
        product.setName("演示商品 7");
        product.setPrice(BigDecimal.valueOf(99.00));
        product.setStock(10);
        product.setSales(3);
        product.setStatus(status);
        return product;
    }

    private static Category category() {
        Category category = new Category();
        category.setId(11L);
        category.setName("手机数码-旗舰");
        return category;
    }

    @Test
    void page_fillsCategoryName() {
        Page<Product> page = new Page<>(1, 10);
        page.setRecords(List.of(product(1)));
        page.setTotal(1L);
        when(productMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(categoryMapper.selectList(isNull())).thenReturn(List.of(category()));

        PageResult<ProductVO> result = productService.page(new ProductQuery(), 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords().get(0).getCategoryName()).isEqualTo("手机数码-旗舰");
    }

    @Test
    void detail_offShelfProduct_throwsProductOffShelf() {
        when(productMapper.selectById(7L)).thenReturn(product(0));

        BusinessException ex = catchBusinessException(() -> productService.detail(7L));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PRODUCT_OFF_SHELF.getCode());
    }

    @Test
    void detail_missingProduct_throwsProductNotFound() {
        when(productMapper.selectById(7L)).thenReturn(null);

        BusinessException ex = catchBusinessException(() -> productService.detail(7L));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    void detail_onSaleProduct_fillsCategoryName() {
        when(productMapper.selectById(7L)).thenReturn(product(1));
        when(categoryMapper.selectList(isNull())).thenReturn(List.of(category()));

        ProductVO vo = productService.detail(7L);

        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getCategoryName()).isEqualTo("手机数码-旗舰");
    }
}
