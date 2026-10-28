package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据访问。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
