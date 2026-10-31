package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.RevokedToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 令牌黑名单数据访问。
 */
@Mapper
public interface RevokedTokenMapper extends BaseMapper<RevokedToken> {
}
