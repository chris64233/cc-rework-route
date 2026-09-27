package com.chris64233.reworkroute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 返工工序完成记录：每个返工子批次的每个工序序号最多一条完成记录，
 * 唯一约束在数据库层面保证并发完成时工序不会被重复登记。
 */
@Entity
@Table(name = "operation_completion", uniqueConstraints = {
        @UniqueConstraint(name = "uk_operation_step", columnNames = {"rework_sub_batch_id", "step_index"})
})
public class OperationCompletion extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ReworkSubBatch reworkSubBatch;

    /** 工序在指定序列中的序号，从 0 开始。 */
    @Column(nullable = false)
    private int stepIndex;

    @Column(nullable = false)
    private String operationCode;

    private String operator;

    protected OperationCompletion() {
    }

    public OperationCompletion(ReworkSubBatch reworkSubBatch, int stepIndex, String operationCode, String operator) {
        this.reworkSubBatch = reworkSubBatch;
        this.stepIndex = stepIndex;
        this.operationCode = operationCode;
        this.operator = operator;
    }

    public ReworkSubBatch getReworkSubBatch() {
        return reworkSubBatch;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public String getOperator() {
        return operator;
    }
}
