package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.entity.enums.NotificationType;
import com.deng.recommend.vo.NotificationVO;

/**
 * 站内通知服务。
 */
public interface NotificationService {

    /**
     * 推送一条通知。
     *
     * <p>实现上不会因为推送失败而影响主业务（例如发货），
     * 通知属于附带动作，不能反过来阻断交易流程。
     *
     * @param userId  接收人
     * @param type    通知类型
     * @param title   标题
     * @param content 正文
     * @param bizType 关联业务类型，可为空
     * @param bizId   关联业务标识，可为空
     */
    void push(Long userId, NotificationType type, String title, String content,
              String bizType, String bizId);

    /**
     * 分页查询我的通知，按时间倒序。
     */
    PageResult<NotificationVO> page(Long userId, long pageNum, long pageSize);

    /**
     * 未读数量，用于导航栏角标。
     */
    long unreadCount(Long userId);

    /**
     * 标记单条已读，只能操作自己的通知。
     */
    void markRead(Long userId, Long id);

    /**
     * 全部标记已读。
     */
    void markAllRead(Long userId);
}
