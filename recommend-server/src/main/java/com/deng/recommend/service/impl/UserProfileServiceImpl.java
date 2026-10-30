package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.BehaviorHistoryRow;
import com.deng.recommend.dto.UpdateProfileRequest;
import com.deng.recommend.entity.Cart;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.Review;
import com.deng.recommend.entity.User;
import com.deng.recommend.entity.enums.OrderStatus;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.CartMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.ReviewMapper;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.service.UserProfileService;
import com.deng.recommend.vo.HistoryVO;
import com.deng.recommend.vo.UserStatsVO;
import com.deng.recommend.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人中心服务实现。
 *
 * <p><b>越权防护</b>：所有方法都以传入的 {@code userId} 作为唯一过滤条件，
 * 且改资料时只接受白名单字段，用户无法通过构造请求体把自己改成管理员。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    /** 浏览足迹包含浏览与点击两种行为 */
    private static final List<String> VIEW_TYPES = List.of("VIEW", "CLICK");

    /** 购买足迹只有 PURCHASE */
    private static final List<String> PURCHASE_TYPES = List.of("PURCHASE");

    private final UserMapper userMapper;
    private final CartMapper cartMapper;
    private final OrderMapper orderMapper;
    private final ReviewMapper reviewMapper;
    private final ProductMapper productMapper;
    private final BehaviorMapper behaviorMapper;

    @Override
    public UserVO getProfile(Long userId) {
        return toUserVO(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);

        // 逐字段赋值，只认白名单；空白字符串统一存 null，便于前端判断"未填写"
        if (request.getNickname() != null) {
            user.setNickname(trimToNull(request.getNickname()));
        }
        if (request.getEmail() != null) {
            user.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getPhone() != null) {
            user.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getAddress() != null) {
            user.setAddress(trimToNull(request.getAddress()));
        }
        if (request.getAvatar() != null) {
            user.setAvatar(trimToNull(request.getAvatar()));
        }

        userMapper.updateById(user);
        log.info("用户资料已更新: userId={}", userId);
        return toUserVO(user);
    }

    @Override
    public UserStatsVO stats(Long userId) {
        Long cartCount = cartMapper.selectCount(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId));

        long pendingPayment = countOrders(userId, OrderStatus.PENDING.getCode());
        long pendingShipment = countOrders(userId, OrderStatus.PAID.getCode());
        long pendingReceipt = countOrders(userId, OrderStatus.SHIPPED.getCode());

        // 待评价 = 已完成订单涉及的商品数 - 已评价数，不能为负
        long finishedProducts = orderMapper.countFinishedDistinctProducts(userId);
        Long reviewed = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId));
        long pendingReview = Math.max(0L, finishedProducts - (reviewed == null ? 0L : reviewed));

        return UserStatsVO.builder()
                .cartCount(cartCount == null ? 0L : cartCount)
                .pendingPayment(pendingPayment)
                .pendingShipment(pendingShipment)
                .pendingReceipt(pendingReceipt)
                .pendingReview(pendingReview)
                .build();
    }

    @Override
    public PageResult<HistoryVO> history(Long userId, String type, long pageNum, long pageSize) {
        List<String> types = resolveTypes(type);
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), 50);
        long offset = (safePage - 1) * safeSize;

        long total = behaviorMapper.countHistory(userId, types);
        if (total == 0 || offset >= total) {
            return new PageResult<>(safePage, safeSize, total, new ArrayList<>());
        }

        List<BehaviorHistoryRow> rows = behaviorMapper.selectHistory(userId, types, offset, safeSize);
        if (rows.isEmpty()) {
            return new PageResult<>(safePage, safeSize, total, new ArrayList<>());
        }

        // 批量取商品，避免 N+1
        List<Long> productIds = new ArrayList<>(rows.size());
        for (BehaviorHistoryRow row : rows) {
            productIds.add(row.getProductId());
        }
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(productIds)) {
            productMap.put(product.getId(), product);
        }

        List<HistoryVO> records = new ArrayList<>(rows.size());
        for (BehaviorHistoryRow row : rows) {
            Product product = productMap.get(row.getProductId());
            // 商品已被删除则跳过，不计入本页
            if (product == null) {
                continue;
            }
            records.add(HistoryVO.of(product, row.getTimes(), row.getLastTime()));
        }
        return new PageResult<>(safePage, safeSize, total, records);
    }

    private List<String> resolveTypes(String type) {
        if (!StringUtils.hasText(type) || "VIEW".equalsIgnoreCase(type)) {
            return VIEW_TYPES;
        }
        if ("PURCHASE".equalsIgnoreCase(type)) {
            return PURCHASE_TYPES;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "type 只支持 VIEW 或 PURCHASE");
    }

    private long countOrders(Long userId, int status) {
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, status));
        return count == null ? 0L : count;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserVO toUserVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
