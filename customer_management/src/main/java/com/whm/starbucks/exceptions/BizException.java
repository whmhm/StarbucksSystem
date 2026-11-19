package com.whm.starbucks.exceptions;

import lombok.Getter;

/**
 * 业务异常：携带语义化状态码（沿用 HTTP 状态码语义，如 400/401），
 * 由 GlobalExceptionHandler 统一转成 ResponseVo.fail(code, message)。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
