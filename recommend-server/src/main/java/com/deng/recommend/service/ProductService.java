package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.dto.ProductQuery;
import com.deng.recommend.vo.ProductVO;

import java.util.List;

/**
 * 商品服务。
 */
public interface ProductService {

    /**
     * 分页查询在售商品。
     *
     * <p>只返回 {@code status = 1} 的上架商品，下架商品对前台不可见；
     * 排序由 {@code sort} 控制，缺省按销量降序。
     *
     * @param query    查询条件（关键字、类目、价格区间、排序）
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页商品视图
     */
    PageResult<ProductVO> page(ProductQuery query, long pageNum, long pageSize);

    /**
     * 查询商品详情。
     *
     * @param id 商品 ID
     * @return 商品视图
     */
    ProductVO detail(Long id);

    /**
     * 按 ID 批量查询商品，用于推荐结果回填商品信息。
     *
     * <p>设计取舍：调用方（推荐服务）可能传入空列表，此处直接返回空列表，
     * 避免 MyBatis-Plus 生成 {@code IN ()} 这类非法 SQL。
     *
     * @param ids 商品 ID 列表
     * @return 商品视图列表，顺序与数据库返回一致
     */
    List<ProductVO> listByIds(List<Long> ids);
}
