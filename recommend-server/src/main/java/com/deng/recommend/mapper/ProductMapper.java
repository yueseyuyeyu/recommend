package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deng.recommend.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 商品数据访问。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 累计销量总和。
     *
     * <p>后台统计只需要一个数字。原先用 {@code selectList(select(sales))} 把全表商品行
     * 拉回内存再求和，商品上万时既慢又占内存；改为数据库端聚合，
     * 传输量与商品数量无关。
     *
     * @return 总销量，无数据返回 0
     */
    @Select("SELECT COALESCE(SUM(sales), 0) FROM product WHERE is_deleted = 0")
    long sumSales();
}
