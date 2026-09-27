package com.chris64233.reworkroute.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 不合格记录：必须关联生产批次，并单独记录受影响数量。
 *
 * <p>affectedQuantity 为原始受影响数量，consumedQuantity 为已被处置决定消费的数量。
 * 已确认处置要求消费数量必须等于受影响数量，因此同一原始数量不可能被两个处置重复消费。</p>
 */
@Entity
@Table(name = "non_conforming_record")
public class NonConformingRecord extends BaseEntity {

    /** 不合格记录业务编号，唯一。 */
    @Column(nullable = false, unique = true)
    private String ncNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ProductionBatch batch;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal affectedQuantity;

    /** 已被已确认处置消费的数量。 */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NcStatus status = NcStatus.OPEN;

    private String defectDescription;

    @Version
    private Long version;

    protected NonConformingRecord() {
    }

    public NonConformingRecord(String ncNo, ProductionBatch batch, BigDecimal affectedQuantity,
                               String defectDescription) {
        this.ncNo = ncNo;
        this.batch = batch;
        this.affectedQuantity = affectedQuantity;
        this.defectDescription = defectDescription;
    }

    /** 尚未被处置消费的剩余数量。 */
    public BigDecimal remainingQuantity() {
        return affectedQuantity.subtract(consumedQuantity);
    }

    public String getNcNo() {
        return ncNo;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public BigDecimal getAffectedQuantity() {
        return affectedQuantity;
    }

    public BigDecimal getConsumedQuantity() {
        return consumedQuantity;
    }

    public void setConsumedQuantity(BigDecimal consumedQuantity) {
        this.consumedQuantity = consumedQuantity;
    }

    public NcStatus getStatus() {
        return status;
    }

    public void setStatus(NcStatus status) {
        this.status = status;
    }

    public String getDefectDescription() {
        return defectDescription;
    }

    public Long getVersion() {
        return version;
    }
}
