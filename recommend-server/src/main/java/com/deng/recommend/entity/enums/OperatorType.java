package com.deng.recommend.entity.enums;

/**
 * 操作者类型，用于订单状态日志区分「谁改的」。
 */
public enum OperatorType {

    /** 买家本人 */
    USER,

    /** 后台管理员 */
    ADMIN,

    /** 系统自动（定时任务等） */
    SYSTEM
}
