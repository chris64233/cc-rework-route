package com.chris64233.reworkroute.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 返工子批次：处置确认时按返工数量原子生成。
 * 必须严格按处置指定的工序顺序完成（completedSteps），全部完成后才能提交复验；
 * 仅当最新版本复验合格且子批次状态为 COMPLETED 时，才可重新并入可用库存。
 */
@Entity
@Table(name = "rework_sub_batch")
public class ReworkSubBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String subBatchNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch parentBatch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "nc_id", nullable = false)
    private NonconformingRecord nc;

    @OneToOne(mappedBy = "reworkSubBatch", fetch = FetchType.EAGER, optional = false)
    private Disposition disposition;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReworkStatus status;

    /** 已完成工序，按完成先后有序保存；必须与处置指定工序的同序条目一致。 */
    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn(name = "seq")
    @AttributeOverride(name = "operationCode", column = @Column(name = "operation_code", nullable = false))
    @AttributeOverride(name = "completedAt", column = @Column(name = "completed_at", nullable = false))
    private List<ReworkStep> completedSteps = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    private Instant mergedAt;

    @Version
    private long version;

    protected ReworkSubBatch() {
    }

    public ReworkSubBatch(String subBatchNo, ProductionBatch parentBatch, NonconformingRecord nc, int quantity) {
        this.subBatchNo = subBatchNo;
        this.parentBatch = parentBatch;
        this.nc = nc;
        this.quantity = quantity;
        this.status = ReworkStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public void markInProgress() {
        this.status = ReworkStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = ReworkStatus.COMPLETED;
    }

    public void markMerged() {
        this.status = ReworkStatus.MERGED;
        this.mergedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSubBatchNo() {
        return subBatchNo;
    }

    public ProductionBatch getParentBatch() {
        return parentBatch;
    }

    public NonconformingRecord getNc() {
        return nc;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReworkStatus getStatus() {
        return status;
    }

    public List<ReworkStep> getCompletedSteps() {
        return completedSteps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getMergedAt() {
        return mergedAt;
    }

    public long getVersion() {
        return version;
    }

    /** 单道工序完成记录（值对象，随子批次持久化）。 */
    @Embeddable
    public static class ReworkStep {
        private String operationCode;
        private Instant completedAt;

        protected ReworkStep() {
        }

        public ReworkStep(String operationCode, Instant completedAt) {
            this.operationCode = operationCode;
            this.completedAt = completedAt;
        }

        public String getOperationCode() {
            return operationCode;
        }

        public Instant getCompletedAt() {
            return completedAt;
        }
    }
}
