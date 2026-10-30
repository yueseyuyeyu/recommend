package com.deng.recommend.vo;

import com.deng.recommend.entity.UserAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收货地址视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressVO {

    private Long id;

    private String receiverName;

    private String receiverPhone;

    private String detailAddress;

    /** 1-默认地址 */
    private Integer isDefault;

    public static AddressVO from(UserAddress address) {
        return AddressVO.builder()
                .id(address.getId())
                .receiverName(address.getReceiverName())
                .receiverPhone(address.getReceiverPhone())
                .detailAddress(address.getDetailAddress())
                .isDefault(address.getIsDefault())
                .build();
    }
}
