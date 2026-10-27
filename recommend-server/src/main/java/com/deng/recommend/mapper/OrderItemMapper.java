package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细数据访问。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
