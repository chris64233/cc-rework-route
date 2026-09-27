package com.chris64233.reworkroute.domain;

/**
 * 复验判定结论。FAIL 时子批次不得并入库存，需要继续返工或追加处置。
 */
public enum ReinspectionDecision {
    PASS,
    FAIL
}
