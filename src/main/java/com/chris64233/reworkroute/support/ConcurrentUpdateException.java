package com.chris64233.reworkroute.support;

/**
 * 并发更新失败（乐观锁版本冲突 / 唯一约束冲突）。映射为 HTTP 409。
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
