package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知数据访问。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
