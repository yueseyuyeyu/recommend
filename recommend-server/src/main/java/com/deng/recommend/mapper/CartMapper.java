package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Cart;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车数据访问。
 */
@Mapper
public interface CartMapper extends BaseMapper<Cart> {
}
