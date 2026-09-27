package com.chris64233.reworkroute.service;

/**
 * 资源不存在（批次号、不合格号、处置业务号、子批次号等）。
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
