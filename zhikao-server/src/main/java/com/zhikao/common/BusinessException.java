package com.zhikao.common;

/**
 * 业务异常：在 Service 层主动抛出，由 {@link GlobalExceptionHandler} 统一转换为 R 结构返回。
 * 与系统异常区分，便于返回明确的业务错误码与提示。
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码，默认 50001（业务处理失败） */
    private final int code;

    public BusinessException(String msg) {
        this(ErrorCode.BUSINESS_ERROR, msg);
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMsg());
    }

    public BusinessException(ErrorCode errorCode, String msg) {
        super(msg);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
