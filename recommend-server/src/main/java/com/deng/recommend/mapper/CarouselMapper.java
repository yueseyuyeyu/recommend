package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Carousel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 首页轮播图数据访问。
 */
@Mapper
public interface CarouselMapper extends BaseMapper<Carousel> {
}
