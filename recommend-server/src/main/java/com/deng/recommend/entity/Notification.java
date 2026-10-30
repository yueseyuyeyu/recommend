package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知，对应表 {@code notification}。
 *
 * <p>本项目没有真实推送通道（短信/邮件/App），所以用站内信承载
 * 「发货提醒」这类消息：写一条记录，前端导航栏铃铛展示未读数。
 * 后续接真实通道时，在推送处补一个发送动作即可，数据模型不用改。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 类型，取值见 {@link com.deng.recommend.entity.enums.NotificationType} */
    private String type;

    private String title;

    private String content;

    /** 关联业务类型，如 ORDER */
    private String bizType;

    /** 关联业务标识，如订单号 */
    private String bizId;

    /** 是否已读：1-是，0-否 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
