package com.chris64233.reworkroute.domain;

/**
 * 返工子批次状态：
 * CREATED 已生成；IN_PROGRESS 工序进行中；COMPLETED 全部指定工序完成；
 * MERGED 凭最新复验合格结果重新并入可用库存。
 */
public enum ReworkStatus {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    MERGED
}
