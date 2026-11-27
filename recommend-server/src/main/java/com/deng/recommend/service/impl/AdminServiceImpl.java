package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.config.OrderProperties;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.entity.Carousel;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.OrderItem;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.User;
import com.deng.recommend.entity.enums.Carrier;
import com.deng.recommend.entity.enums.NotificationType;
import com.deng.recommend.entity.enums.OperatorType;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.entity.enums.PayType;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CarouselMapper;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.mapper.OrderItemMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.security.UserStatusCache;
import com.deng.recommend.service.AdminService;
import com.deng.recommend.service.CategoryService;
import com.deng.recommend.service.NotificationService;
import com.deng.recommend.service.OrderService;
import com.deng.recommend.service.OrderStatusLogService;
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 批量上下架单次上限，与 DTO 校验保持一致 */
    private static final int MAX_BATCH_SIZE = 200;

    /** 导出时每条 SQL 取多少行：兼顾往返次数与内存占用 */
    private static final int EXPORT_BATCH_SIZE = 500;

    /** CSV 里的时间格式：用空格分隔而非 ISO 的 T，Excel 才能直接识别为时间 */
    private static final java.time.format.DateTimeFormatter CSV_TIME_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final CarouselMapper carouselMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final BehaviorMapper behaviorMapper;
    private final OrderService orderService;
    private final OrderStatusLogService orderStatusLogService;
    private final NotificationService notificationService;
    private final OrderProperties orderProperties;
    private final UserStatusCache userStatusCache;
    private final CategoryService categoryService;

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
    @Transactional(rollbackFor = Exception.class)
    public int updateProductStatusBatch(List<Long> ids, Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0（下架）或 1（上架）");
        }
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要操作的商品");
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "单次最多操作 " + MAX_BATCH_SIZE + " 个商品");
        }

        // 一条 UPDATE 完成，避免逐个调用产生 N 次往返
        int affected = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .in(Product::getId, ids)
                .set(Product::getStatus, status));

        log.info("批量修改商品状态, status={}, 请求={} 个, 实际影响={}", status, ids.size(), affected);
        return affected;
    }

    @Override
    public int exportOrders(Integer status, OutputStream out) {
        int total = 0;
        long lastId = 0L;

        try {
            // UTF-8 BOM：不加的话 Excel 打开中文列名会乱码
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write("订单号,状态,金额,支付方式,下单时间,支付时间,配送方,物流单号,收货人,电话,收货地址\n");

            // 游标分批：按主键递增翻页，边查边写。
            // 用 id > lastId 而不是 OFFSET，深翻页时不会越翻越慢，
            // 也不会因为并发插入导致同一条订单重复导出。
            while (true) {
                List<Order> batch = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .gt(Order::getId, lastId)
                        .eq(status != null, Order::getStatus, status)
                        .orderByAsc(Order::getId)
                        .last("LIMIT " + EXPORT_BATCH_SIZE));

                if (batch.isEmpty()) {
                    break;
                }
                for (Order order : batch) {
                    writer.write(toCsvRow(order));
                    lastId = order.getId();
                }
                total += batch.size();
                // 及时 flush，让浏览器开始收数据，而不是等全部查完才吐
                writer.flush();
            }
            writer.flush();
        } catch (IOException e) {
            log.error("导出订单 CSV 失败，已写出 {} 行", total, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "导出失败，请稍后重试");
        }

        log.info("订单导出完成, status={}, 行数={}", status, total);
        return total;
    }

    /** 拼一行 CSV，含逗号/引号/换行的字段加引号并转义 */
    private String toCsvRow(Order order) {
        OrderStatus status = order.getStatusEnum();
        return String.join(",",
                csv(order.getOrderNo()),
                csv(status == null ? "" : status.getName()),
                csv(order.getTotalAmount()),
                csv(PayType.nameOf(order.getPayType())),
                csv(formatTime(order.getCreateTime())),
                csv(formatTime(order.getPaidTime())),
                csv(Carrier.nameOf(order.getCarrier())),
                csv(order.getTrackingNo()),
                csv(order.getReceiverName()),
                csv(order.getReceiverPhone()),
                csv(order.getReceiverAddr())) + "\n";
    }

    private String formatTime(java.time.LocalDateTime time) {
        return time == null ? "" : time.format(CSV_TIME_FORMAT);
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    @Override
    public PageResult<ProductVO> pageProducts(String keyword, Integer status, String sortOrder,
                                              long pageNum, long pageSize) {
        // 后台按 ID 排序：ID 是自增主键，天然连续，不会出现按创建时间排序时
        // 「同一秒创建的多条记录 ID 交错」的观感混乱。方向由前端表头切换。
        boolean desc = isDesc(sortOrder);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(StringUtils.hasText(keyword), Product::getName, keyword)
                .eq(status != null, Product::getStatus, status);
        if (desc) {
            wrapper.orderByDesc(Product::getId);
        } else {
            wrapper.orderByAsc(Product::getId);
        }
        Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 复用类目服务的名称映射（60 秒 TTL 快照），不再每请求全量查类目表
        Map<Long, String> categoryNames = categoryService.categoryNameMap();
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
        // 类目快照缓存主动失效，名称映射与子孙索引立即反映变更
        categoryService.evictCaches();
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
        categoryService.evictCaches();
    }

    @Override
    public PageResult<UserVO> pageUsers(String keyword, String sortOrder, long pageNum, long pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        // 与商品一致：按自增 ID 排序，方向可切换，保证列表顺序连续可预期
        if (isDesc(sortOrder)) {
            wrapper.orderByDesc(User::getId);
        } else {
            wrapper.orderByAsc(User::getId);
        }

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
        // 禁用/启用后立刻让状态缓存失效，旧令牌无需等待缓存 TTL 才失效
        userStatusCache.evict(id);
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
            orderService.cancelByAdmin(orderNo);
            return;
        }

        // 发货必须带物流单号，走专门的 ship 接口
        if (target == OrderStatus.SHIPPED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发货请通过发货操作选择配送方");
        }

        // 确认收货有 7 天时效门槛，通用改状态接口绕不过去
        if (target == OrderStatus.FINISHED) {
            int days = orderProperties.getAutoReceiptDays();
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "确认收货请通过确认收货操作" + (days > 0 ? "，需发货满 " + days + " 天" : ""));
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(target.getCode());
        orderMapper.updateById(update);

        orderStatusLogService.record(orderNo, current.getCode(), target.getCode(),
                OperatorType.ADMIN, SecurityUtils.currentUserId(), currentOperatorName(), "管理员变更状态");

        log.info("订单状态变更, orderNo={}, {} -> {}", orderNo, current.getName(), target.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String ship(String orderNo, String carrierCode, String trackingNo) {
        Carrier carrier = Carrier.of(carrierCode);

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

        // 单号留空就按配送方规则自动生成，正常流程只需选快递公司
        String finalTrackingNo = StringUtils.hasText(trackingNo)
                ? trackingNo.trim()
                : carrier.generateTrackingNo();

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(OrderStatus.SHIPPED.getCode());
        update.setCarrier(carrier.getCode());
        update.setTrackingNo(finalTrackingNo);
        update.setShipTime(LocalDateTime.now());
        orderMapper.updateById(update);

        String remark = "配送方：" + carrier.getDisplayName() + "，单号：" + finalTrackingNo;
        orderStatusLogService.record(orderNo, OrderStatus.PAID.getCode(), OrderStatus.SHIPPED.getCode(),
                OperatorType.ADMIN, SecurityUtils.currentUserId(), currentOperatorName(), remark);

        notificationService.push(order.getUserId(), NotificationType.ORDER_SHIPPED,
                "订单已发货",
                "您的订单 " + orderNo + " 已由" + carrier.getDisplayName() + "发出，物流单号 "
                        + finalTrackingNo + "，请注意查收。",
                "ORDER", orderNo);

        log.info("订单发货, orderNo={}, carrier={}, trackingNo={}",
                orderNo, carrier.getCode(), finalTrackingNo);
        return finalTrackingNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        OrderStatus current = OrderStatus.of(order.getStatus());
        if (!current.canTransitionTo(OrderStatus.FINISHED)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单当前为「" + current.getName() + "」，不能确认收货");
        }

        int days = orderProperties.getAutoReceiptDays();
        if (days > 0) {
            LocalDateTime shipTime = order.getShipTime();
            if (shipTime == null) {
                throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL, "该订单缺少发货时间，无法确认收货");
            }
            if (shipTime.plusDays(days).isAfter(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.ORDER_STATUS_ILLEGAL,
                        "发货满 " + days + " 天后才能确认收货，当前尚未到期");
            }
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(OrderStatus.FINISHED.getCode());
        orderMapper.updateById(update);

        orderStatusLogService.record(orderNo, OrderStatus.SHIPPED.getCode(), OrderStatus.FINISHED.getCode(),
                OperatorType.ADMIN, SecurityUtils.currentUserId(), currentOperatorName(), "管理员确认收货");

        log.info("管理员确认收货, orderNo={}", orderNo);
    }

    @Override
    public Map<String, Object> statistics() {
        // 聚合在数据库完成：商品量大时不再把全表行拉进内存求和
        long totalSales = productMapper.sumSales();

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

    /**
     * 解析排序方向：只有明确传 {@code desc} 才降序，其余（含 null、空串、非法值）一律升序。
     *
     * <p>白名单式判断而非直接拼进 SQL，避免任何外部输入影响排序字段。
     */
    private boolean isDesc(String sortOrder) {
        return "desc".equalsIgnoreCase(sortOrder == null ? null : sortOrder.trim());
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
                .carrier(order.getCarrier())
                .carrierName(Carrier.nameOf(order.getCarrier()))
                .shipTime(order.getShipTime())
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
     * 取当前管理员用户名，作为状态日志的操作者快照。
     */
    private String currentOperatorName() {
        try {
            return SecurityUtils.currentUsername();
        } catch (RuntimeException e) {
            return null;
        }
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
