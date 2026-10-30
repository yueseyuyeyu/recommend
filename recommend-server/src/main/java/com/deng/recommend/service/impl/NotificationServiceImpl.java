package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.entity.Notification;
import com.deng.recommend.entity.enums.NotificationType;
import com.deng.recommend.mapper.NotificationMapper;
import com.deng.recommend.service.NotificationService;
import com.deng.recommend.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 站内通知实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void push(Long userId, NotificationType type, String title, String content,
                     String bizType, String bizId) {
        if (userId == null || type == null || title == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type.name());
            notification.setTitle(title);
            notification.setContent(content);
            notification.setBizType(bizType);
            notification.setBizId(bizId);
            notification.setIsRead(0);
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 通知是附带动作，失败不能影响发货等主流程
            log.warn("站内通知写入失败, userId={}, type={}", userId, type, e);
        }
    }

    @Override
    public PageResult<NotificationVO> page(Long userId, long pageNum, long pageSize) {
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), 50);

        Page<Notification> page = notificationMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getId));

        List<NotificationVO> records = new ArrayList<>(page.getRecords().size());
        for (Notification item : page.getRecords()) {
            records.add(NotificationVO.from(item));
        }
        return new PageResult<>(safePage, safeSize, page.getTotal(), records);
    }

    @Override
    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return count == null ? 0L : count;
    }

    @Override
    public void markRead(Long userId, Long id) {
        Notification exists = notificationMapper.selectById(id);
        // 不属于自己的通知按「不存在」处理，不暴露他人数据是否存在
        if (exists == null || !exists.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        if (Integer.valueOf(1).equals(exists.getIsRead())) {
            return;
        }
        Notification update = new Notification();
        update.setId(id);
        update.setIsRead(1);
        notificationMapper.updateById(update);
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
