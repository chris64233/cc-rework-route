package com.chris64233.reworkroute.domain;

/**
 * 处置单生命周期状态。
 */
public enum DispositionStatus {
    /** 已提出，处置决定尚未确认，数量未被消费。 */
    PENDING,
    /** 已确认，子批次 / 报废 / 让步记录已原子生成，数量已消费。 */
    CONFIRMED
}
