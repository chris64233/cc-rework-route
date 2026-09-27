package com.chris64233.reworkroute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * 生产批次。availableQuantity 为当前可直接使用的数量：
 * 初始等于 initialQuantity；不合格冻结、报废、返工、让步与并回都会通过库存移动记录调整，
 * 恒等式 initialQuantity = available + 各在途（冻结/返工） + 已报废。
 */
@Entity
@Table(name = "production_batch")
public class ProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchNo;

    @Column(nullable = false)
    private String productCode;

    /** 批次投产总数量，不可变。 */
    @Column(nullable = false)
    private int initialQuantity;

    /** 当前可用数量（让步释放、返工并回会增加；冻结、报废会减少）。 */
    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private Instant createdAt;

    /** JPA 乐观锁，作为行级悲观锁之外的第二道并发保护。 */
    @Version
    private long version;

    protected ProductionBatch() {
    }

    public ProductionBatch(String batchNo, String productCode, int initialQuantity) {
        this.batchNo = batchNo;
        this.productCode = productCode;
        this.initialQuantity = initialQuantity;
        this.availableQuantity = initialQuantity;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getProductCode() {
        return productCode;
    }

    public int getInitialQuantity() {
        return initialQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
