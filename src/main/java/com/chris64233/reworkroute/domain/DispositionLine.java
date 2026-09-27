package com.chris64233.reworkroute.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 处置拆分行：每一个非零去向（返工 / 报废 / 让步接收）对应一行，
 * 记录该去向的数量及其落账结果（返工子批次 / 报废记录）。
 */
@Entity
@Table(name = "disposition_line")
public class DispositionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private DispositionOrder disposition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DispositionType type;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    /** 返工去向生成的返工子批次。 */
    @ManyToOne(fetch = FetchType.EAGER)
    private ReworkSubBatch reworkSubBatch;

    /** 报废去向生成的报废记录。 */
    @ManyToOne(fetch = FetchType.EAGER, cascade = jakarta.persistence.CascadeType.PERSIST)
    private ScrapRecord scrapRecord;

    protected DispositionLine() {
    }

    public DispositionLine(DispositionOrder disposition, DispositionType type, BigDecimal quantity) {
        this.disposition = disposition;
        this.type = type;
        this.quantity = quantity;
    }

    public DispositionType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public ReworkSubBatch getReworkSubBatch() {
        return reworkSubBatch;
    }

    public void setReworkSubBatch(ReworkSubBatch reworkSubBatch) {
        this.reworkSubBatch = reworkSubBatch;
    }

    public ScrapRecord getScrapRecord() {
        return scrapRecord;
    }

    public void setScrapRecord(ScrapRecord scrapRecord) {
        this.scrapRecord = scrapRecord;
    }
}
