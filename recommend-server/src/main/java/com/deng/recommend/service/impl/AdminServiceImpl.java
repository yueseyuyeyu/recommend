package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.entity.Carousel;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.OrderItem;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.User;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.entity.enums.PayType;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CarouselMapper;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.mapper.OrderItemMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.service.AdminService;
import com.deng.recommend.service.OrderService;
import com.deng.recommend.vo.CarouselVO;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final CarouselMapper carouselMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final BehaviorMapper behaviorMapper;
    private final OrderService orderService;

    /** 类目最大层级：一级 → 二级 → 三级 */
    private static final int MAX_CATEGORY_LEVEL = 3;

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
        // 前端会把四级选项置灰，但接口不能只靠前端把关
        if (category.getParentId() != 0L) {
            ensureDepthAllowed(category.getParentId());
        }
        if (category.getId() == null) {
            categoryMapper.insert(category);
        } else {
            categoryMapper.updateById(category);
        }
        return category.getId();
    }

    @Override
    public void deleteCategory(Long id) {        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
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

        OrderStatus current = OrderStatus.of(order.getStatus());
        OrderStatus target = OrderStatus.of(status);

        // 重复提交同一个状态，直接当成功处理
        if (current == target) {
            return;
        }

        if (!current.canTransitionTo(target)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单当前为「" + current.getName() + "」，不能变更为「" + target.getName() + "」");
        }

        // 取消复用用户侧逻辑，库存回滚只写一份，避免两边行为不一致
        if (target == OrderStatus.CANCELLED) {
            orderService.cancel(order.getUserId(), orderNo);
            log.info("后台取消订单, orderNo={}", orderNo);
            return;
        }

        // 发货必须带物流单号，走专门的 ship 接口
        if (target == OrderStatus.SHIPPED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发货请通过发货操作填写物流单号");
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(target.getCode());
        orderMapper.updateById(update);
        log.info("订单状态变更, orderNo={}, {} -> {}", orderNo, current.getName(), target.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(String orderNo, String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写物流单号");
        }

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        OrderStatus current = OrderStatus.of(order.getStatus());
        if (!current.canTransitionTo(OrderStatus.SHIPPED)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单当前为「" + current.getName() + "」，不能发货");
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(OrderStatus.SHIPPED.getCode());
        update.setTrackingNo(trackingNo.trim());
        orderMapper.updateById(update);

        log.info("订单发货, orderNo={}, trackingNo={}", orderNo, trackingNo.trim());
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
                .payType(order.getPayType())
                .payTypeName(PayType.nameOf(order.getPayType()))
                .paidTime(order.getPaidTime())
                .trackingNo(order.getTrackingNo())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .receiverAddr(order.getReceiverAddr())
                .createTime(order.getCreateTime())
                .items(items)
                .build();
    }

    /* ==================== 轮播图管理 ==================== */

    @Override
    public List<CarouselVO> listCarousels() {
        List<Carousel> list = carouselMapper.selectList(new LambdaQueryWrapper<Carousel>()
                .orderByAsc(Carousel::getSortOrder)
                .orderByAsc(Carousel::getId));
        List<CarouselVO> vos = new ArrayList<>(list.size());
        for (Carousel carousel : list) {
            vos.add(CarouselVO.from(carousel));
        }
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarouselVO saveCarousel(Carousel carousel) {
        if (!StringUtils.hasText(carousel.getTitle())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "轮播图标题不能为空");
        }
        if (carousel.getSortOrder() == null) {
            carousel.setSortOrder(0);
        }
        if (carousel.getStatus() == null) {
            carousel.setStatus(1);
        }
        // 没给渐变色时兜底，避免前端背景变透明
        if (!StringUtils.hasText(carousel.getBgColor())) {
            carousel.setBgColor("linear-gradient(135deg,#ff6a00,#ee0979)");
        }

        if (carousel.getId() == null) {
            carouselMapper.insert(carousel);
        } else {
            if (carouselMapper.selectById(carousel.getId()) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "轮播图不存在");
            }
            carouselMapper.updateById(carousel);
        }
        return CarouselVO.from(carouselMapper.selectById(carousel.getId()));
    }

    @Override
    public void deleteCarousel(Long id) {
        if (carouselMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "轮播图不存在");
        }
        carouselMapper.deleteById(id);
    }

    @Override
    public void updateCarouselStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
        Carousel exists = carouselMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "轮播图不存在");
        }
        Carousel update = new Carousel();
        update.setId(id);
        update.setStatus(status);
        carouselMapper.updateById(update);
    }

    /**
     * 校验在给定父级下再挂一层是否会超出层级上限。
     *
     * <p>做法是从父级往上数祖先，父级深度 + 1 就是新节点的深度。
     * 用 visited 兜住数据异常造成的环，避免死循环。
     */
    private void ensureDepthAllowed(Long parentId) {
        int parentDepth = 0;
        Long current = parentId;
        Set<Long> visited = new HashSet<>();

        while (current != null && current != 0L && visited.add(current)) {
            parentDepth++;
            Category parent = categoryMapper.selectById(current);
            current = parent == null ? 0L : parent.getParentId();
        }

        if (parentDepth + 1 > MAX_CATEGORY_LEVEL) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "类目最多支持 " + MAX_CATEGORY_LEVEL + " 级");
        }
    }
}
