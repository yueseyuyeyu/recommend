package com.deng.recommend.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于表达可预期的业务失败（如库存不足、商品下架），
 * 由 {@link GlobalExceptionHandler} 捕获并转换为结构化响应，
 * 不暴露堆栈给前端。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
