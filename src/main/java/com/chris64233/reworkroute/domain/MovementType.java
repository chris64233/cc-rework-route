package com.chris64233.reworkroute.domain;

/**
 * 库存（数量）移动类型，完整描述不合格数量的去向，保证谱系可核对：
 * BLOCK 从可用库存冻结为不合格受影响数量；
 * TO_REWORK 从冻结数量转入返工子批次；
 * SCRAP 从冻结数量报废出库；
 * CONCESSION_RELEASE 让步接收，冻结数量重新回到可用库存；
 * REWORK_MERGE 返工复验合格后并入可用库存。
 */
public enum MovementType {
    BLOCK,
    TO_REWORK,
    SCRAP,
    CONCESSION_RELEASE,
    REWORK_MERGE
}
