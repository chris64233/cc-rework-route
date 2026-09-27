package com.chris64233.reworkroute.domain;

/**
 * 处置单状态。DRAFT 可重放（幂等）但不能落地任何处置结果；
 * CONFIRMED 之后处置内容冻结，只允许追加纠正记录。
 */
public enum DispositionStatus {
    DRAFT,
    CONFIRMED
}
