package com.chris64233.reworkroute.service;

/**
 * 请求内容不合法（数量为负、三部分之和不等于受影响数量等），映射 HTTP 400。
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
