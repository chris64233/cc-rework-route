package com.chris64233.reworkroute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 返工指定工序的有序条目（由处置决定给出）。
 */
@Entity
@Table(name = "rework_operation")
public class ReworkOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "step_index", insertable = false, updatable = false)
    private Integer stepIndex;

    @Column(nullable = false)
    private String operationCode;

    protected ReworkOperation() {
    }

    public ReworkOperation(String operationCode) {
        this.operationCode = operationCode;
    }

    public Long getId() {
        return id;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public String getOperationCode() {
        return operationCode;
    }
}
