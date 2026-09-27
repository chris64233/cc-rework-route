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
 * 不合格记录：关联生产批次并单独记录受影响数量 affectedQuantity。
 * 处置确认前该数量从批次可用库存冻结；确认后按处置决定一次性路由，
 * 因此同一份受影响数量不可能被两个处置重复消费。
 */
@Entity
@Table(name = "nonconforming_record")
public class NonconformingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ncNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    @Column(nullable = false)
    private String defectCode;

    /** 受影响数量，单独记录；处置拆分三部分之和必须与之相等。 */
    @Column(nullable = false)
    private int affectedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NcStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    /** 处置确认时间，用于谱系与审计。 */
    private Instant disposedAt;

    @Version
    private long version;

    protected NonconformingRecord() {
    }

    public NonconformingRecord(String ncNo, ProductionBatch batch, String defectCode, int affectedQuantity) {
        this.ncNo = ncNo;
        this.batch = batch;
        this.defectCode = defectCode;
        this.affectedQuantity = affectedQuantity;
        this.status = NcStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNcNo() {
        return ncNo;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public String getDefectCode() {
        return defectCode;
    }

    public int getAffectedQuantity() {
        return affectedQuantity;
    }

    public NcStatus getStatus() {
        return status;
    }

    public void markDisposed() {
        this.status = NcStatus.DISPOSED;
        this.disposedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDisposedAt() {
        return disposedAt;
    }

    public long getVersion() {
        return version;
    }
}
