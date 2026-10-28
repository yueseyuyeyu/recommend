package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Behavior;
import com.deng.recommend.recommend.model.BehaviorRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为数据访问，推荐算法的数据入口。
 */
@Mapper
public interface BehaviorMapper extends BaseMapper<Behavior> {

    /**
     * 分页加载行为记录，用于构建推荐矩阵。
     *
     * <p>使用 {@code LIMIT} 限制单次加载量，防止数据量增长后拖垮接口性能。
     * 所有参数均以 #{ } 形式绑定，不存在 SQL 注入风险。
     *
     * @param since 起始时间，为空则不限
     * @param limit 最大返回行数
     * @return 行为记录列表
     */
    @Select({
            "<script>",
            "SELECT user_id, product_id, weight, created_at FROM behavior",
            "WHERE 1 = 1",
            "<if test='since != null'> AND created_at &gt;= #{since} </if>",
            "ORDER BY created_at DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    @Results({
            @Result(property = "userId", column = "user_id"),
            @Result(property = "productId", column = "product_id"),
            @Result(property = "weight", column = "weight"),
            @Result(property = "occurredAt", column = "created_at")
    })
    List<BehaviorRecord> selectBehaviorRecords(@Param("since") LocalDateTime since, @Param("limit") int limit);
}
