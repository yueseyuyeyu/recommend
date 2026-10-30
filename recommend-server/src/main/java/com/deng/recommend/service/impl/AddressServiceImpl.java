package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.dto.AddressRequest;
import com.deng.recommend.entity.UserAddress;
import com.deng.recommend.mapper.UserAddressMapper;
import com.deng.recommend.service.AddressService;
import com.deng.recommend.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 收货地址簿实现。
 *
 * <p><b>默认地址唯一性</b>：数据库没法用简单约束表达「同一用户仅一行为 1」，
 * 所以在每次设置默认时先执行一条 UPDATE 把该用户所有地址置 0，再置目标为 1，
 * 两步放在同一个事务里，避免出现两条默认。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final UserAddressMapper userAddressMapper;

    @Override
    public List<AddressVO> list(Long userId) {
        List<UserAddress> rows = userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreateTime));

        List<AddressVO> vos = new ArrayList<>(rows.size());
        for (UserAddress row : rows) {
            vos.add(AddressVO.from(row));
        }
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO create(Long userId, AddressRequest request) {
        boolean first = countOf(userId) == 0;
        boolean shouldBeDefault = Boolean.TRUE.equals(request.getIsDefault()) || first;

        if (shouldBeDefault) {
            clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(request.getReceiverName().trim());
        address.setReceiverPhone(request.getReceiverPhone().trim());
        address.setDetailAddress(request.getDetailAddress().trim());
        address.setIsDefault(shouldBeDefault ? 1 : 0);
        userAddressMapper.insert(address);

        return AddressVO.from(userAddressMapper.selectById(address.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO update(Long userId, Long id, AddressRequest request) {
        UserAddress exists = requireOwned(userId, id);

        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            clearDefault(userId);
        }

        exists.setReceiverName(request.getReceiverName().trim());
        exists.setReceiverPhone(request.getReceiverPhone().trim());
        exists.setDetailAddress(request.getDetailAddress().trim());
        if (makeDefault) {
            exists.setIsDefault(1);
        }
        userAddressMapper.updateById(exists);

        return AddressVO.from(userAddressMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        UserAddress exists = requireOwned(userId, id);
        userAddressMapper.deleteById(id);

        // 删掉的是默认地址时，把剩下最新的一条提为默认，避免结算时没有默认项
        if (Integer.valueOf(1).equals(exists.getIsDefault())) {
            List<UserAddress> rest = userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getCreateTime));
            if (!rest.isEmpty()) {
                UserAddress next = new UserAddress();
                next.setId(rest.get(0).getId());
                next.setIsDefault(1);
                userAddressMapper.updateById(next);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        requireOwned(userId, id);
        clearDefault(userId);

        UserAddress update = new UserAddress();
        update.setId(id);
        update.setIsDefault(1);
        userAddressMapper.updateById(update);
    }

    @Override
    public AddressVO getOwned(Long userId, Long id) {
        return AddressVO.from(requireOwned(userId, id));
    }

    private long countOf(Long userId) {
        Long count = userAddressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
        return count == null ? 0L : count;
    }

    /**
     * 把该用户所有地址的默认标记清掉。
     */
    private void clearDefault(Long userId) {
        userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0));
    }

    private UserAddress requireOwned(Long userId, Long id) {
        UserAddress address = userAddressMapper.selectById(id);
        // 不属于当前用户时按「不存在」处理，不暴露他人地址是否存在
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }
}
