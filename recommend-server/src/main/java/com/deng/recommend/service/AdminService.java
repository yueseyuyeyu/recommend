package com.deng.recommend.service;

import com.deng.recommend.entity.Product;
import com.deng.recommend.vo.ProductVO;

import java.util.Map;

/**
 * 后台管理服务。
 *
 * <p>接口访问级别由 {@code SecurityConfig} 限定为 ADMIN 角色，服务层只负责业务逻辑。
 */
public interface AdminService {

    /**
     * 新增或更新商品。
     *
     * @param product 商品实体，id 为空表示新增
     * @return 商品视图
     */
    ProductVO saveProduct(Product product);

    /**
     * 逻辑删除商品。
     *
     * @param id 商品 ID
     */
    void deleteProduct(Long id);

    /**
     * 修改商品上下架状态。
     *
     * @param id     商品 ID
     * @param status 1-上架，0-下架
     */
    void updateProductStatus(Long id, Integer status);

    /**
     * 汇总后台首页统计指标。
     *
     * @return 统计指标键值对
     */
    Map<String, Object> statistics();
}
