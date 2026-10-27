package com.deng.recommend.service;

import com.deng.recommend.dto.CartAddRequest;
import com.deng.recommend.dto.CartUpdateRequest;
import com.deng.recommend.vo.CartVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车服务。
 *
 * <p>所有接口均以登录用户 ID 为查询维度，杜绝通过请求参数越权操作他人购物车。
 */
public interface CartService {

    /**
     * 加入购物车，同一商品重复加入则累加数量。
     *
     * @param userId  当前登录用户 ID
     * @param request 加购请求
     */
    void add(Long userId, CartAddRequest request);

    /**
     * 查询当前用户的购物车列表，并关联商品信息计算小计。
     *
     * @param userId 当前登录用户 ID
     * @return 购物车条目视图列表
     */
    List<CartVO> list(Long userId);

    /**
     * 修改购物车条目的数量或勾选状态。
     *
     * @param userId  当前登录用户 ID
     * @param cartId  购物车条目 ID
     * @param request 修改请求
     */
    void update(Long userId, Long cartId, CartUpdateRequest request);

    /**
     * 删除指定购物车条目。
     *
     * @param userId 当前登录用户 ID
     * @param cartId 购物车条目 ID
     */
    void remove(Long userId, Long cartId);

    /**
     * 清空当前用户的购物车。
     *
     * @param userId 当前登录用户 ID
     */
    void clear(Long userId);

    /**
     * 统计已勾选商品的总金额。
     *
     * @param userId 当前登录用户 ID
     * @return 勾选商品合计金额
     */
    BigDecimal totalChecked(Long userId);
}
