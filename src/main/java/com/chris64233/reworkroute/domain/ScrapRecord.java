package com.chris64233.reworkroute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 报废记录：处置确认时按报废数量原子生成。
 */
@Entity
@Table(name = "scrap_record")
public class ScrapRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String scrapNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "nc_id", nullable = false)
    private NonconformingRecord nc;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private String reasonCode;

    @Column(nullable = false)
    private Instant createdAt;

    protected ScrapRecord() {
    }

    public ScrapRecord(String scrapNo, ProductionBatch batch, NonconformingRecord nc, int quantity,
                       String reasonCode) {
        this.scrapNo = scrapNo;
        this.batch = batch;
        this.nc = nc;
        this.quantity = quantity;
        this.reasonCode = reasonCode;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getScrapNo() {
        return scrapNo;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public NonconformingRecord getNc() {
        return nc;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
