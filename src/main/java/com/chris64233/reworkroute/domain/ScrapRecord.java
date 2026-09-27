package com.chris64233.reworkroute.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 报废记录：确认处置时原子生成，数量从可用库存永久核销。
 */
@Entity
@Table(name = "scrap_record")
public class ScrapRecord extends BaseEntity {

    /** 报废记录业务编号。 */
    @Column(nullable = false, unique = true)
    private String scrapNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ProductionBatch batch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private DispositionOrder disposition;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    private String reason;

    protected ScrapRecord() {
    }

    public ScrapRecord(String scrapNo, ProductionBatch batch, DispositionOrder disposition,
                       BigDecimal quantity, String reason) {
        this.scrapNo = scrapNo;
        this.batch = batch;
        this.disposition = disposition;
        this.quantity = quantity;
        this.reason = reason;
    }

    public String getScrapNo() {
        return scrapNo;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public DispositionOrder getDisposition() {
        return disposition;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }
}
