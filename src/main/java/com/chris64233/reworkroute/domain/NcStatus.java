package com.chris64233.reworkroute.domain;

/**
 * 不合格记录的生命周期状态。
 * OPEN：尚未确认处置；DISPOSED：处置已确认，受影响数量全部完成路由。
 */
public enum NcStatus {
    OPEN,
    DISPOSED
}
