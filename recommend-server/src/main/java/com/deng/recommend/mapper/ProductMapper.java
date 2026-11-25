package com.deng.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

    /**
     * 关键词全文检索（ngram 索引），按相关度倒序。
     *
     * <p>为什么单独写 SQL 而不是拼 Wrapper：
     * 相关度排序需要 {@code ORDER BY MATCH(...) AGAINST(...)} 这样的表达式，
     * 而 MyBatis-Plus 的 {@code orderByDesc} 只接受列名——把关键词拼进 ORDER BY
     * 就等于开了注入口子。这里用 {@code #{kw}} 预编译参数，两处匹配都是安全的。
     *
     * <p>检索时忽略用户选择的排序方式：搜索场景下「最相关」优先于「最新」，
     * 否则搜「手机」可能返回一堆不相关的上新商品。
     */
    @Select("<script>"
            + "SELECT * FROM product "
            + "WHERE is_deleted = 0 AND status = 1 "
            + "  AND MATCH(name, subtitle) AGAINST(#{kw} IN BOOLEAN MODE) "
            + "<if test='categoryIds != null and categoryIds.size() > 0'>"
            + "  AND category_id IN "
            + "  <foreach collection='categoryIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>"
            + "</if>"
            + "<if test='minPrice != null'> AND price &gt;= #{minPrice}</if>"
            + "<if test='maxPrice != null'> AND price &lt;= #{maxPrice}</if>"
            + " ORDER BY MATCH(name, subtitle) AGAINST(#{kw} IN BOOLEAN MODE) DESC, id DESC"
            + "</script>")
    IPage<Product> searchByKeyword(IPage<Product> page,
                                   @Param("kw") String keyword,
                                   @Param("categoryIds") List<Long> categoryIds,
                                   @Param("minPrice") BigDecimal minPrice,
                                   @Param("maxPrice") BigDecimal maxPrice);
}
