package com.chris64233.reworkroute.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 生产批次。availableQuantity 为当前可直接使用的库存数量。
 */
@Entity
@Table(name = "production_batch")
public class ProductionBatch extends BaseEntity {

    /** 批次业务编号，唯一。 */
    @Column(nullable = false, unique = true)
    private String batchNo;

    /** 物料编码。 */
    @Column(nullable = false)
    private String materialCode;

    /** 原始生产数量。 */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal totalQuantity;

    /** 当前可用数量：登记不合格、报废会扣减；返工复验合格后回补。 */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal availableQuantity;

    protected ProductionBatch() {
    }

    public ProductionBatch(String batchNo, String materialCode, BigDecimal totalQuantity) {
        this.batchNo = batchNo;
        this.materialCode = materialCode;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = totalQuantity;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(BigDecimal availableQuantity) {
        this.availableQuantity = availableQuantity;
    }
}
