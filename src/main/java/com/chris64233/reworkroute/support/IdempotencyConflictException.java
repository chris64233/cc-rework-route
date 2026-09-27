package com.chris64233.reworkroute.support;

/**
 * 幂等冲突：同一业务号重放但提交内容与首次不一致。映射为 HTTP 409。
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
