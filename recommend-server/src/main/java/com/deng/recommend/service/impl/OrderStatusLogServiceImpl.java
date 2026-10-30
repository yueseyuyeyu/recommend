package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.entity.OrderStatusLog;
import com.deng.recommend.entity.enums.OperatorType;
import com.deng.recommend.mapper.OrderStatusLogMapper;
import com.deng.recommend.service.OrderStatusLogService;
import com.deng.recommend.vo.OrderStatusLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单状态日志实现。
 *
 * <p><b>为什么不做异常吞掉</b>：日志与状态变更在同一个事务里，
 * 写日志失败通常意味着数据库本身有问题，这时候把状态改成功反而更危险
 * （出现「状态变了但查不到是谁改的」）。让它一起回滚，问题会立刻暴露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusLogServiceImpl implements OrderStatusLogService {

    private final OrderStatusLogMapper orderStatusLogMapper;

    @Override
    public void record(String orderNo, Integer fromStatus, Integer toStatus,
                       OperatorType operatorType, Long operatorId, String operatorName, String remark) {
        OrderStatusLog entity = new OrderStatusLog();
        entity.setOrderNo(orderNo);
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setOperatorType(operatorType == null ? OperatorType.SYSTEM.name() : operatorType.name());
        entity.setOperatorId(operatorId);
        entity.setOperatorName(operatorName);
        entity.setRemark(remark);
        orderStatusLogMapper.insert(entity);

        log.debug("订单状态日志, orderNo={}, {} -> {}, by {}",
                orderNo, fromStatus, toStatus, entity.getOperatorType());
    }

    @Override
    public List<OrderStatusLogVO> listByOrderNo(String orderNo) {
        List<OrderStatusLog> logs = orderStatusLogMapper.selectList(
                new LambdaQueryWrapper<OrderStatusLog>()
                        .eq(OrderStatusLog::getOrderNo, orderNo)
                        .orderByAsc(OrderStatusLog::getId));

        List<OrderStatusLogVO> vos = new ArrayList<>(logs.size());
        for (OrderStatusLog item : logs) {
            vos.add(OrderStatusLogVO.from(item));
        }
        return vos;
    }
}
