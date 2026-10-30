package com.deng.recommend.service;

import com.deng.recommend.entity.enums.OperatorType;
import com.deng.recommend.vo.OrderStatusLogVO;

import java.util.List;

/**
 * 订单状态日志服务。
 */
public interface OrderStatusLogService {

    /**
     * 记录一次状态变更。
     *
     * @param orderNo      订单号
     * @param fromStatus   变更前状态码，订单创建事件传 null
     * @param toStatus     变更后状态码
     * @param operatorType 操作者类型
     * @param operatorId   操作者 ID，系统操作传 null
     * @param operatorName 操作者名称
     * @param remark       备注，如物流单号
     */
    void record(String orderNo, Integer fromStatus, Integer toStatus,
                OperatorType operatorType, Long operatorId, String operatorName, String remark);

    /**
     * 查询某个订单的完整变更历史，按发生时间正序。
     */
    List<OrderStatusLogVO> listByOrderNo(String orderNo);
}
