package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Review;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品评价数据访问。
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
