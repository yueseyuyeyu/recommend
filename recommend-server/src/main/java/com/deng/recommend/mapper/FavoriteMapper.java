package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品收藏数据访问。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
