package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.config.OrderProperties;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.User;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CarouselMapper;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.mapper.OrderItemMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.security.UserStatusCache;
import com.deng.recommend.service.NotificationService;
import com.deng.recommend.service.OrderService;
import com.deng.recommend.service.OrderStatusLogService;
import com.deng.recommend.support.MybatisLambdaTestSupport;
import com.deng.recommend.vo.ProductVO;
import com.deng.recommend.vo.UserVO;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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

    @Mock
    private CarouselMapper carouselMapper;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderStatusLogService orderStatusLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserStatusCache userStatusCache;

    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        MybatisLambdaTestSupport.init();
        adminService = new AdminServiceImpl(
                productMapper, categoryMapper, carouselMapper, userMapper, orderMapper, orderItemMapper,
                behaviorMapper, orderService, orderStatusLogService, notificationService, new OrderProperties(),
                userStatusCache);
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
    void updateUserStatus_success_evictsStatusCache() {
        when(userMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        adminService.updateUserStatus(1L, 0);

        // 禁用后立即清缓存，旧令牌不必等待缓存 TTL 才失效
        verify(userStatusCache).evict(1L);
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

    @Test
    void updateProductStatusBatch_invalidStatus_throwsParamError() {
        ThrowingCallable call = () -> adminService.updateProductStatusBatch(List.of(1L), 2);
        BusinessException ex = (BusinessException) catchThrowable(call);
        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verifyNoInteractions(productMapper);
    }

    @Test
    void updateProductStatusBatch_emptyIds_throwsParamError() {
        ThrowingCallable call = () -> adminService.updateProductStatusBatch(List.of(), 1);
        BusinessException ex = (BusinessException) catchThrowable(call);
        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    void updateProductStatusBatch_overLimit_throwsParamError() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 201; i++) {
            ids.add(i);
        }
        ThrowingCallable call = () -> adminService.updateProductStatusBatch(ids, 1);
        BusinessException ex = (BusinessException) catchThrowable(call);
        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(ex.getMessage()).contains("200");
    }

    @Test
    void updateProductStatusBatch_returnsAffectedCount() {
        when(productMapper.update(isNull(), any(Wrapper.class))).thenReturn(3);

        int affected = adminService.updateProductStatusBatch(List.of(1L, 2L, 5L), 0);

        assertThat(affected).isEqualTo(3);
    }

    @Test
    void exportOrders_writesBomAndRows() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("NO20260101000001");
        order.setStatus(OrderStatus.PAID.getCode());
        order.setTotalAmount(new BigDecimal("99.50"));
        order.setReceiverName("张三,李四");
        // 第一次返回一行，第二次为空表示取完
        when(orderMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(order))
                .thenReturn(List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rows = adminService.exportOrders(null, out);

        assertThat(rows).isEqualTo(1);
        byte[] bytes = out.toByteArray();
        // UTF-8 BOM：Excel 不加会乱码
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);

        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("订单号").contains("NO20260101000001").contains("已支付");
        // 含逗号的字段必须被引号包裹，否则列会错位
        assertThat(text).contains("\"张三,李四\"");
    }
}
