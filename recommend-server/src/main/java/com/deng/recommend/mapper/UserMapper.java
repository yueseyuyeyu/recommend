package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问。
 *
 * <p>SQL 全部由 MyBatis-Plus 生成并使用参数化查询，
 * 杜绝参考项目中 {@code "where aNo like '%"+x+"%'"} 的字符串拼接写法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
