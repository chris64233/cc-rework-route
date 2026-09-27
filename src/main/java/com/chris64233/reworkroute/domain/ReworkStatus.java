package com.chris64233.reworkroute.domain;

/**
 * 返工子批次状态。
 */
public enum ReworkStatus {
    /** 返工中，指定工序尚未全部完成。 */
    IN_PROGRESS,
    /** 工序全部完成，等待或正在复验。 */
    OPERATIONS_DONE,
    /** 复验合格，已重新并入可用库存。 */
    REINTEGRATED
}
