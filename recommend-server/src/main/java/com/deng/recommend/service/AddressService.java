package com.deng.recommend.service;

import com.deng.recommend.dto.AddressRequest;
import com.deng.recommend.vo.AddressVO;

import java.util.List;

/**
 * 收货地址簿服务。
 *
 * <p>所有方法都以 {@code userId} 为过滤条件，「最多一条默认地址」的约束
 * 在服务层统一维护，前端不需要关心。
 */
public interface AddressService {

    /**
     * 查询我的全部地址，默认地址排最前，其余按创建时间倒序。
     */
    List<AddressVO> list(Long userId);

    /**
     * 新增地址。
     *
     * <p>首条地址会自动成为默认；显式传 {@code isDefault=true} 时也会抢占默认。
     */
    AddressVO create(Long userId, AddressRequest request);

    /**
     * 修改地址，仅限本人。
     */
    AddressVO update(Long userId, Long id, AddressRequest request);

    /**
     * 删除地址，仅限本人。若删掉的是默认地址，自动把最新的一条提为默认。
     */
    void delete(Long userId, Long id);

    /**
     * 设为默认地址。
     */
    void setDefault(Long userId, Long id);

    /**
     * 取一条属于自己的地址，用于下单时读取收货信息。
     *
     * @throws com.deng.recommend.common.BusinessException 地址不存在或不属于当前用户
     */
    AddressVO getOwned(Long userId, Long id);
}
