package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 类目数据访问。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
