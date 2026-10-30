package com.deng.recommend.vo;

import com.deng.recommend.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    private Long id;

    private String type;

    private String title;

    private String content;

    private String bizType;

    private String bizId;

    /** 1-已读，0-未读 */
    private Integer isRead;

    private LocalDateTime createTime;

    public static NotificationVO from(Notification notification) {
        return NotificationVO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .bizType(notification.getBizType())
                .bizId(notification.getBizId())
                .isRead(notification.getIsRead())
                .createTime(notification.getCreateTime())
                .build();
    }
}
