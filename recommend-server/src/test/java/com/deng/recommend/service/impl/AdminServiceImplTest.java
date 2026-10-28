package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.User;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.mapper.OrderItemMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.support.MybatisLambdaTestSupport;
import com.deng.recommend.vo.ProductVO;
import com.deng.recommend.vo.UserVO;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private BehaviorMapper behaviorMapper;

    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        MybatisLambdaTestSupport.init();
        adminService = new AdminServiceImpl(
                productMapper, categoryMapper, userMapper, orderMapper, orderItemMapper, behaviorMapper);
    }

    private static BusinessException catchBusinessException(ThrowingCallable callable) {
        Throwable throwable = catchThrowable(callable);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }

    @Test
    void updateProductStatus_illegalStatus_throwsParamError() {
        BusinessException ex = catchBusinessException(() -> adminService.updateProductStatus(1L, 2));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verifyNoInteractions(productMapper);
    }

    @Test
    void updateProductStatus_productNotFound_throwsProductNotFound() {
        when(productMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        BusinessException ex = catchBusinessException(() -> adminService.updateProductStatus(1L, 0));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    void updateUserStatus_userNotFound_throwsNotFound() {
        when(userMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        BusinessException ex = catchBusinessException(() -> adminService.updateUserStatus(1L, 0));

        assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void updateUserStatus_illegalStatus_throwsParamError() {
        BusinessException ex = catchBusinessException(() -> adminService.updateUserStatus(1L, 2));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verifyNoInteractions(userMapper);
    }

    @Test
    void pageOrders_illegalStatus_throwsParamError() {
        BusinessException ex = catchBusinessException(() -> adminService.pageOrders(99, 1, 10));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verifyNoInteractions(orderMapper, orderItemMapper);
    }

    @Test
    void updateOrderStatus_illegalStatus_throwsParamError() {
        BusinessException ex = catchBusinessException(() -> adminService.updateOrderStatus("NO1", 99));

        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verifyNoInteractions(orderMapper);
    }

    @Test
    void updateOrderStatus_cancelledOrder_throwsOrderStatusIllegal() {
        Order order = new Order();
        order.setOrderNo("NO1");
        order.setStatus(OrderStatus.CANCELLED.getCode());
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);

        BusinessException ex = catchBusinessException(
                () -> adminService.updateOrderStatus("NO1", OrderStatus.PAID.getCode()));

        assertThat(ex.getCode()).isEqualTo(ResultCode.ORDER_STATUS_ILLEGAL.getCode());
    }

    @Test
    void pageProducts_offShelfProduct_fillsCategoryName() {
        Product product = new Product();
        product.setId(7L);
        product.setCategoryId(11L);
        product.setName("演示商品 7");
        product.setStatus(0);

        Page<Product> page = new Page<>(1, 10);
        page.setRecords(List.of(product));
        page.setTotal(1L);
        when(productMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Category category = new Category();
        category.setId(11L);
        category.setName("手机数码-旗舰");
        when(categoryMapper.selectList(isNull())).thenReturn(List.of(category));

        PageResult<ProductVO> result = adminService.pageProducts("演示", 0, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getCategoryName()).isEqualTo("手机数码-旗舰");
        assertThat(result.getRecords().get(0).getStatus()).isZero();
    }

    @Test
    void pageUsers_disabledUser_exposesStatusWithoutPassword() {
        User user = new User();
        user.setId(3L);
        user.setUsername("user002");
        user.setPasswordHash("should-not-leak");
        user.setRole(1);
        user.setStatus(0);

        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of(user));
        page.setTotal(1L);
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<UserVO> result = adminService.pageUsers("user", 1, 10);

        UserVO vo = result.getRecords().get(0);
        assertThat(vo.getStatus()).isZero();
        assertThat(vo.getUsername()).isEqualTo("user002");
        assertThat(vo.getToken()).isNull();
    }
}
