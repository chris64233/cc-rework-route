package com.chris64233.reworkroute.support;

/**
 * 引用对象（批次 / 不合格记录 / 子批次等）不存在。映射为 HTTP 404。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
