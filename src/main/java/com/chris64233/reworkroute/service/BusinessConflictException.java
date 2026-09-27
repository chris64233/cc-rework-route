package com.chris64233.reworkroute.service;

/**
 * 业务规则冲突，映射 HTTP 409：
 * 业务号内容不一致的重放、受影响数量已被处置、工序顺序错误、
 * 复验未完成/不合格即放行、旧版本结果放行、处置已确认后尝试修改等。
 */
public class BusinessConflictException extends RuntimeException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
