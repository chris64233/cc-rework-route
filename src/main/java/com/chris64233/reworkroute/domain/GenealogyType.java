package com.chris64233.reworkroute.domain;

/**
 * 批次谱系流转类型。
 */
public enum GenealogyType {
    /** 原批次 → 返工子批次。 */
    REWORK,
    /** 原批次 → 报废（终点）。 */
    SCRAP,
    /** 原批次 → 让步接收（终点，数量可用）。 */
    CONCESSION,
    /** 返工子批次 → 原批次，复验合格重新并入可用库存。 */
    REINTEGRATE
}
