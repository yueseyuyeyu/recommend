package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单数据访问。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 统计某用户已完成订单里涉及的不同商品数。
     *
     * <p>用于个人中心「待评价」：已完成订单的商品数减去已评价数即为待评价数。
     * 用 DISTINCT 是因为同一商品可能出现在多个订单里，评价却只评一次。
     *
     * <p>订单表名是 {@code orders}（避开 MySQL 关键字 order），需加反引号。
     *
     * @param userId 用户 ID
     * @return 已完成订单涉及的不同商品数
     */
    @Select("SELECT COUNT(DISTINCT oi.product_id) "
            + "FROM `orders` o "
            + "JOIN order_item oi ON oi.order_no = o.order_no "
            + "WHERE o.user_id = #{userId} AND o.status = 3 AND o.is_deleted = 0")
    long countFinishedDistinctProducts(@Param("userId") Long userId);
}
