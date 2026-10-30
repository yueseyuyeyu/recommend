package com.deng.recommend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建订单请求。
 *
 * <p>收货信息有两种来源，二选一：
 * <ol>
 *   <li><b>addressId</b>：从地址簿选一条，服务端回查该地址并校验归属，
 *       请求体里的收货信息一律忽略——避免客户端伪造收货地址</li>
 *   <li>手填 {@code receiverName / receiverPhone / receiverAddr}：兼容地址簿之前的调用方</li>
 * </ol>
 * 两者都不提供时由服务层抛出参数错误。
 */
@Data
public class OrderCreateRequest {

    /** 收货地址 ID，来自地址簿 */
    private Long addressId;

    /** 支付方式码：1-支付宝，2-微信支付，3-银行卡；下单时选定，支付时确认 */
    private Integer payType;

    @Size(max = 64, message = "收货人过长")
    private String receiverName;

    @Size(max = 32, message = "联系电话过长")
    private String receiverPhone;

    @Size(max = 255, message = "收货地址过长")
    private String receiverAddr;
}
