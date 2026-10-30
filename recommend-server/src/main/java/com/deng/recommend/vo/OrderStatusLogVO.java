package com.deng.recommend.vo;

import com.deng.recommend.entity.OrderStatusLog;
import com.deng.recommend.entity.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单状态变更日志视图，供订单详情的时间轴展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusLogVO {

    private Long id;

    /** 变更前状态码，创建事件为 null */
    private Integer fromStatus;

    /** 变更前状态名，创建事件为 null */
    private String fromStatusName;

    private Integer toStatus;

    private String toStatusName;

    /** USER / ADMIN / SYSTEM */
    private String operatorType;

    /** 操作者类型的中文名 */
    private String operatorTypeName;

    private String operatorName;

    private String remark;

    private LocalDateTime createTime;

    public static OrderStatusLogVO from(OrderStatusLog log) {
        return OrderStatusLogVO.builder()
                .id(log.getId())
                .fromStatus(log.getFromStatus())
                .fromStatusName(statusName(log.getFromStatus()))
                .toStatus(log.getToStatus())
                .toStatusName(statusName(log.getToStatus()))
                .operatorType(log.getOperatorType())
                .operatorTypeName(operatorTypeName(log.getOperatorType()))
                .operatorName(log.getOperatorName())
                .remark(log.getRemark())
                .createTime(log.getCreateTime())
                .build();
    }

    private static String statusName(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return OrderStatus.of(code).getName();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    private static String operatorTypeName(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "USER" -> "买家";
            case "ADMIN" -> "管理员";
            case "SYSTEM" -> "系统";
            default -> type;
        };
    }
}
