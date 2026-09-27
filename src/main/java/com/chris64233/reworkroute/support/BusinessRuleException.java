package com.chris64233.reworkroute.support;

/**
 * 业务规则校验失败：数量不平、状态非法、顺序违反等。
 * 映射为 HTTP 422。
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
