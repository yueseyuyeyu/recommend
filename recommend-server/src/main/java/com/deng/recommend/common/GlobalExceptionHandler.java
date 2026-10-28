package com.deng.recommend.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>把所有异常转换为统一的 {@link Result} 响应体，
 * 前端永远拿到结构化错误，而不是 HTML 错误页或堆栈信息。
 *
 * <p>参考项目虽然有 {@code NewBeeMallExceptionHandler}，
 * 但商城端算法异常仍会直接冒泡；本项目统一收敛。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：返回对应错误码与提示。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常：拼接字段级错误提示，便于前端定位到具体输入框。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ResultCode.PARAM_ERROR.getCode(),
                message.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleNotReadableException(HttpMessageNotReadableException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    /**
     * 兜底异常：记录完整堆栈，但只返回通用提示，不泄露内部实现。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部错误", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
