package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Product;
import com.deng.recommend.vo.OrderVO;
import com.deng.recommend.vo.ProductVO;
import com.deng.recommend.vo.UserVO;

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
     * 分页查询商品（后台，含已下架商品）。
     *
     * <p>前台分页接口只返回上架商品，后台需要管理全部商品，
     * 因此单独提供本方法，避免管理员改错状态后商品从列表里消失。
     *
     * @param keyword  商品名称关键字，可为空
     * @param status   上下架状态，为空表示不限
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页商品列表，含类目名称
     */
    PageResult<ProductVO> pageProducts(String keyword, Integer status, long pageNum, long pageSize);

    /**
     * 新增或更新类目。
     *
     * @param category 类目实体，id 为空表示新增
     * @return 类目 ID
     */
    Long saveCategory(Category category);

    /**
     * 逻辑删除类目，存在子类目或商品引用时拒绝删除。
     *
     * @param id 类目 ID
     */
    void deleteCategory(Long id);

    /**
     * 分页查询用户（后台）。
     *
     * @param keyword  用户名或昵称关键字，可为空
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页用户列表
     */
    PageResult<UserVO> pageUsers(String keyword, long pageNum, long pageSize);

    /**
     * 启用或禁用用户。
     *
     * @param id     用户 ID
     * @param status 1-正常，0-禁用
     */
    void updateUserStatus(Long id, Integer status);

    /**
     * 分页查询全部订单（后台）。
     *
     * @param status   订单状态，为空表示不限
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页订单列表
     */
    PageResult<OrderVO> pageOrders(Integer status, long pageNum, long pageSize);

    /**
     * 修改订单状态。
     *
     * @param orderNo 订单号
     * @param status  目标状态
     */
    void updateOrderStatus(String orderNo, Integer status);

    /**
     * 汇总后台首页统计指标。
     *
     * @return 统计指标键值对
     */
    Map<String, Object> statistics();
}
