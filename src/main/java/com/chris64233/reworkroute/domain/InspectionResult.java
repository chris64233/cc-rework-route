package com.chris64233.reworkroute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 复验结果：按返工子批次 + 版本号唯一，版本号由小到大依次登记。
 * 放行只看最新版本：若旧版本合格而新版本不合格，旧版本不得放行。
 */
@Entity
@Table(name = "inspection_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inspection_version", columnNames = {"rework_sub_batch_id", "version_no"})
})
public class InspectionResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ReworkSubBatch reworkSubBatch;

    /** 复验版本号，从 1 开始单调递增。 */
    @Column(name = "version_no", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private InspectionVerdict verdict;

    private String inspector;

    private String remark;

    protected InspectionResult() {
    }

    public InspectionResult(ReworkSubBatch reworkSubBatch, int version, InspectionVerdict verdict,
                            String inspector, String remark) {
        this.reworkSubBatch = reworkSubBatch;
        this.version = version;
        this.verdict = verdict;
        this.inspector = inspector;
        this.remark = remark;
    }

    public ReworkSubBatch getReworkSubBatch() {
        return reworkSubBatch;
    }

    public int getVersion() {
        return version;
    }

    public InspectionVerdict getVerdict() {
        return verdict;
    }

    public String getInspector() {
        return inspector;
    }

    public String getRemark() {
        return remark;
    }
}
