package com.chris64233.reworkroute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * 复验结果，带单调版本号 resultVersion。同一子批次可多次复验（返工后再次检验），
 * 只有最新版本的合格结论能放行；并发提交下较小版本号（旧结果）不得放行。
 */
@Entity
@Table(name = "reinspection_result")
public class ReinspectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rework_sub_batch_id", nullable = false)
    private ReworkSubBatch subBatch;

    /** 单调递增版本号，从 1 开始；放行只认最新版本。 */
    @Column(nullable = false)
    private int resultVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ReinspectionDecision decision;

    @Column(length = 500)
    private String remark;

    @Column(nullable = false)
    private String inspector;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected ReinspectionResult() {
    }

    public ReinspectionResult(ReworkSubBatch subBatch, int resultVersion, ReinspectionDecision decision,
                              String remark, String inspector) {
        this.subBatch = subBatch;
        this.resultVersion = resultVersion;
        this.decision = decision;
        this.remark = remark;
        this.inspector = inspector;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ReworkSubBatch getSubBatch() {
        return subBatch;
    }

    public int getResultVersion() {
        return resultVersion;
    }

    public ReinspectionDecision getDecision() {
        return decision;
    }

    public String getRemark() {
        return remark;
    }

    public String getInspector() {
        return inspector;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
