package com.chris64233.reworkroute.domain;

/**
 * 不合格记录闭环状态。
 */
public enum NcStatus {
    /** 已登记，等待处置。 */
    OPEN,
    /** 处置已确认且存在返工部分，返工与复验尚未全部完成。 */
    IN_REWORK,
    /** 闭环完成：无返工部分时确认即关闭；有返工部分时复验合格并入库存后关闭。 */
    CLOSED
}
