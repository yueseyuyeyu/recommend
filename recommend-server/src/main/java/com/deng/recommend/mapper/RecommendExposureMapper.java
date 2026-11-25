package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.RecommendExposure;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推荐曝光数据访问。
 */
@Mapper
public interface RecommendExposureMapper extends BaseMapper<RecommendExposure> {

    /**
     * 批量写入曝光记录。
     *
     * <p>用一条多值 INSERT 而不是循环单条：一次推荐流要写十几行，
     * 循环写会把一次页面加载放大成十几次数据库往返。
     */
    @Insert("<script>"
            + "INSERT INTO recommend_exposure (user_id, product_id, scene, position, exposed_at) VALUES "
            + "<foreach collection='list' item='e' separator=','>"
            + "(#{e.userId}, #{e.productId}, #{e.scene}, #{e.position}, #{e.exposedAt})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<RecommendExposure> list);

    /** 窗口内的曝光总数 */
    @Select("SELECT COUNT(*) FROM recommend_exposure WHERE exposed_at >= #{since}")
    long countExposures(@Param("since") LocalDateTime since);

    /**
     * 窗口内「曝光后被点击」的记录数。
     *
     * <p>判定口径：同一用户在同一商品上的 CLICK 行为发生在其曝光之后、
     * 且不超过一天。这样点击必须晚于曝光，避免把曝光前的点击算进来；
     * 一天的上界则防止把很久之后的复访算作本次推荐的功劳。
     */
    @Select("SELECT COUNT(*) FROM recommend_exposure e WHERE e.exposed_at >= #{since} "
            + "AND EXISTS (SELECT 1 FROM behavior b "
            + "  WHERE b.user_id = e.user_id AND b.product_id = e.product_id "
            + "    AND b.behavior_type = 'CLICK' "
            + "    AND b.created_at >= e.exposed_at "
            + "    AND b.created_at <= DATE_ADD(e.exposed_at, INTERVAL 1 DAY))")
    long countClickedExposures(@Param("since") LocalDateTime since);
}
