package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.OrderItem;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.User;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.mapper.OrderItemMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.service.AdminService;
import com.deng.recommend.vo.OrderItemVO;
import com.deng.recommend.vo.OrderVO;
import com.deng.recommend.vo.ProductVO;
import com.deng.recommend.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台管理服务实现。
 *
 * <p>商品与类目删除走 MyBatis-Plus 逻辑删除，保留历史订单与评价的引用完整性；
 * 表数据规模有限，统计采用 count 直查，避免一次拉取全表行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final BehaviorMapper behaviorMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO saveProduct(Product product) {
        if (!StringUtils.hasText(product.getName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "商品名称不能为空");
        }
        if (product.getPrice() == null || product.getPrice().signum() < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "商品价格不能为负数");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "库存不能为负数");
        }
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        if (product.getId() == null) {
            productMapper.insert(product);
            log.info("新增商品成功, productId={}", product.getId());
        } else {
            productMapper.updateById(product);
            log.info("更新商品成功, productId={}", product.getId());
        }
        return ProductVO.from(product);
    }

    @Override
    public void deleteProduct(Long id) {
        productMapper.deleteById(id);
        log.info("逻辑删除商品成功, productId={}", id);
    }

    @Override
    public void updateProductStatus(Long id, Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0（下架）或 1（上架）");
        }
        int affected = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStatus, status));
        if (affected == 0) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
    }

    @Override
    public PageResult<ProductVO> pageProducts(String keyword, Integer status, long pageNum, long pageSize) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(StringUtils.hasText(keyword), Product::getName, keyword)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreateTime)
                .orderByDesc(Product::getId);
        Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 类目表规模很小，一次查全量做内存映射，避免逐行联表
        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        List<ProductVO> records = page.getRecords().stream()
                .map(product -> {
                    ProductVO vo = ProductVO.from(product);
                    vo.setCategoryName(categoryNames.get(product.getCategoryId()));
                    return vo;
                })
                .collect(Collectors.toList());
        return PageResult.of(page, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCategory(Category category) {
        if (!StringUtils.hasText(category.getName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "类目名称不能为空");
        }
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        if (category.getId() == null) {
            categoryMapper.insert(category);
        } else {
            categoryMapper.updateById(category);
        }
        return category.getId();
    }

    @Override
    public void deleteCategory(Long id) {
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先删除子类目");
        }
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该类目下仍有商品，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public PageResult<UserVO> pageUsers(String keyword, long pageNum, long pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<UserVO> records = page.getRecords().stream()
                .map(this::toUserVO)
                .collect(Collectors.toList());
        return PageResult.of(page, records);
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0（禁用）或 1（正常）");
        }
        int affected = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getStatus, status));
        if (affected == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
    }

    @Override
    public PageResult<OrderVO> pageOrders(Integer status, long pageNum, long pageSize) {
        if (status != null) {
            // 提前校验状态合法性，避免把非法值带到 SQL 条件里
            checkStatus(status);
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime);
        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<String> orderNos = new ArrayList<>(page.getRecords().size());
        for (Order order : page.getRecords()) {
            orderNos.add(order.getOrderNo());
        }
        Map<String, List<OrderItemVO>> itemMap = loadItems(orderNos);
        List<OrderVO> records = page.getRecords().stream()
                .map(order -> toOrderVO(order, itemMap.getOrDefault(order.getOrderNo(), Collections.emptyList())))
                .collect(Collectors.toList());
        return PageResult.of(page, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(String orderNo, Integer status) {
        checkStatus(status);
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (OrderStatus.CANCELLED == OrderStatus.of(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL, "已取消的订单不可再变更");
        }
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .set(Order::getStatus, status);
        if (OrderStatus.PAID == OrderStatus.of(status) && order.getPaidTime() == null) {
            wrapper.set(Order::getPaidTime, LocalDateTime.now());
        }
        orderMapper.update(null, wrapper);
        log.info("订单状态变更, orderNo={}, status={}", orderNo, status);
    }

    @Override
    public Map<String, Object> statistics() {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().select(Product::getSales));
        int totalSales = products.stream()
                .map(Product::getSales)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("productCount", productMapper.selectCount(null));
        data.put("orderCount", orderMapper.selectCount(null));
        data.put("behaviorCount", behaviorMapper.selectCount(null));
        data.put("totalSales", totalSales);
        return data;
    }

    private void checkStatus(Integer status) {
        try {
            OrderStatus.of(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "未知的订单状态: " + status);
        }
    }

    private Map<String, List<OrderItemVO>> loadItems(List<String> orderNos) {
        if (orderNos.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderNo, orderNos));
        return items.stream()
                .collect(Collectors.groupingBy(
                        OrderItem::getOrderNo,
                        Collectors.mapping(OrderItemVO::from, Collectors.toList())));
    }

    private UserVO toUserVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }

    private OrderVO toOrderVO(Order order, List<OrderItemVO> items) {
        return OrderVO.builder()
                .orderNo(order.getOrderNo())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .statusName(OrderStatus.of(order.getStatus()).getName())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .receiverAddr(order.getReceiverAddr())
                .createTime(order.getCreateTime())
                .items(items)
                .build();
    }
}
