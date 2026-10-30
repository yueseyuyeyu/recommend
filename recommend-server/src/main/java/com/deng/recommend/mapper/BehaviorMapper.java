package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.dto.BehaviorHistoryRow;
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

    /**
     * 按商品聚合查询某用户的行为足迹。
     *
     * <p><b>为什么要聚合</b>：同一商品会被多次浏览，直接按行为行分页会出现
     * 同一个商品在列表里连排十几格。这里按 {@code product_id} 分组，
     * 取该商品最后一次行为时间与累计次数，一个商品只占一行。
     *
     * <p><b>为什么要传 Types 列表</b>：「浏览过」要同时包含 VIEW 与 CLICK；
     * 「买过」只有 PURCHASE。用 IN 而非等值判断，避免上层写两套 SQL。
     *
     * @param userId 用户 ID
     * @param types  行为类型集合，不能为空
     * @param offset 跳过行数
     * @param limit  返回行数
     * @return 聚合行列表，按最后行为时间倒序
     */
    @Select({
            "<script>",
            "SELECT product_id AS productId,",
            "       MAX(created_at) AS lastTime,",
            "       COUNT(*) AS times",
            "FROM behavior",
            "WHERE user_id = #{userId}",
            "  AND behavior_type IN",
            "  <foreach collection='types' item='type' open='(' separator=',' close=')'>#{type}</foreach>",
            "GROUP BY product_id",
            "ORDER BY lastTime DESC",
            "LIMIT #{offset}, #{limit}",
            "</script>"
    })
    List<BehaviorHistoryRow> selectHistory(@Param("userId") Long userId,
                                           @Param("types") List<String> types,
                                           @Param("offset") long offset,
                                           @Param("limit") long limit);

    /**
     * 统计某用户行为足迹涉及的商品数（聚合后的总数，用于分页）。
     */
    @Select({
            "<script>",
            "SELECT COUNT(DISTINCT product_id) FROM behavior",
            "WHERE user_id = #{userId}",
            "  AND behavior_type IN",
            "  <foreach collection='types' item='type' open='(' separator=',' close=')'>#{type}</foreach>",
            "</script>"
    })
    long countHistory(@Param("userId") Long userId, @Param("types") List<String> types);
}
