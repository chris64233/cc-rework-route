package com.chris64233.reworkroute.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 返工子批次：确认处置时由返工去向原子生成。
 *
 * <p>必须按 requiredOperationCount 指定的工序顺序完成（completedOperationCount 之前的工序
 * 不得跳过）；工序全部完成后提交复验，复验结果带单调递增版本号，
 * 只有最新版本复验合格才能重新并入可用库存。</p>
 */
@Entity
@Table(name = "rework_sub_batch")
public class ReworkSubBatch extends BaseEntity {

    /** 返工子批次业务编号，唯一。 */
    @Column(nullable = false, unique = true)
    private String subBatchNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ProductionBatch parentBatch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private NonConformingRecord ncRecord;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private DispositionOrder disposition;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    /** 指定返工工序，逗号分隔，按顺序完成。 */
    @Column(nullable = false, length = 1000)
    private String requiredOperations;

    @Column(nullable = false)
    private int requiredOperationCount;

    /** 已按顺序完成的工序数。 */
    @Column(nullable = false)
    private int completedOperationCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReworkStatus status = ReworkStatus.IN_PROGRESS;

    /** 已登记复验结果的最新版本号，从 0 开始单调递增。 */
    @Column(nullable = false)
    private int inspectionVersion;

    /** 最新版本复验是否合格。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private InspectionVerdict latestVerdict;

    private Instant reintegratedAt;

    @Version
    private Long version;

    protected ReworkSubBatch() {
    }

    public ReworkSubBatch(String subBatchNo, ProductionBatch parentBatch, NonConformingRecord ncRecord,
                          DispositionOrder disposition, BigDecimal quantity, String requiredOperations,
                          int requiredOperationCount) {
        this.subBatchNo = subBatchNo;
        this.parentBatch = parentBatch;
        this.ncRecord = ncRecord;
        this.disposition = disposition;
        this.quantity = quantity;
        this.requiredOperations = requiredOperations;
        this.requiredOperationCount = requiredOperationCount;
        this.completedOperationCount = 0;
    }

    public boolean allOperationsCompleted() {
        return completedOperationCount >= requiredOperationCount;
    }

    public String getSubBatchNo() {
        return subBatchNo;
    }

    public ProductionBatch getParentBatch() {
        return parentBatch;
    }

    public NonConformingRecord getNcRecord() {
        return ncRecord;
    }

    public DispositionOrder getDisposition() {
        return disposition;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getRequiredOperations() {
        return requiredOperations;
    }

    public int getRequiredOperationCount() {
        return requiredOperationCount;
    }

    public int getCompletedOperationCount() {
        return completedOperationCount;
    }

    public void setCompletedOperationCount(int completedOperationCount) {
        this.completedOperationCount = completedOperationCount;
    }

    public ReworkStatus getStatus() {
        return status;
    }

    public void setStatus(ReworkStatus status) {
        this.status = status;
    }

    public int getInspectionVersion() {
        return inspectionVersion;
    }

    public void setInspectionVersion(int inspectionVersion) {
        this.inspectionVersion = inspectionVersion;
    }

    public InspectionVerdict getLatestVerdict() {
        return latestVerdict;
    }

    public void setLatestVerdict(InspectionVerdict latestVerdict) {
        this.latestVerdict = latestVerdict;
    }

    public Instant getReintegratedAt() {
        return reintegratedAt;
    }

    public void setReintegratedAt(Instant reintegratedAt) {
        this.reintegratedAt = reintegratedAt;
    }

    public Long getVersion() {
        return version;
    }
}
