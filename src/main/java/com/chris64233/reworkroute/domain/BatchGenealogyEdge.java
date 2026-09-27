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
 * 批次谱系边：记录不合格数量的每一次流转，构成完整谱系。
 *
 * <ul>
 *   <li>REWORK：原批次 → 返工子批次（确认处置时生成）</li>
 *   <li>SCRAP：原批次 → 报废记录（数量核销，终点）</li>
 *   <li>CONCESSION：原批次 → 让步接收（数量仍可用，终点）</li>
 *   <li>REINTEGRATE：返工子批次 → 原批次（复验合格重新并入可用库存）</li>
 * </ul>
 */
@Entity
@Table(name = "batch_genealogy_edge")
public class BatchGenealogyEdge extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GenealogyType type;

    /** 起点批次：REWORK/SCRAP/CONCESSION 为原批次；REINTEGRATE 为空（起点是返工子批次）。 */
    @ManyToOne(fetch = FetchType.EAGER)
    private ProductionBatch sourceBatch;

    /** 终点批次：仅 REINTEGRATE 指向原批次；其余为空（终点由 reworkSubBatch/scrapRecord 表达）。 */
    @ManyToOne(fetch = FetchType.EAGER)
    private ProductionBatch targetBatch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private NonConformingRecord ncRecord;

    @ManyToOne(fetch = FetchType.EAGER)
    private DispositionOrder disposition;

    @ManyToOne(fetch = FetchType.EAGER)
    private ReworkSubBatch reworkSubBatch;

    @ManyToOne(fetch = FetchType.EAGER)
    private ScrapRecord scrapRecord;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    protected BatchGenealogyEdge() {
    }

    private BatchGenealogyEdge(Builder builder) {
        this.type = builder.type;
        this.sourceBatch = builder.sourceBatch;
        this.targetBatch = builder.targetBatch;
        this.ncRecord = builder.ncRecord;
        this.disposition = builder.disposition;
        this.reworkSubBatch = builder.reworkSubBatch;
        this.scrapRecord = builder.scrapRecord;
        this.quantity = builder.quantity;
    }

    public static Builder builder(GenealogyType type, NonConformingRecord ncRecord, BigDecimal quantity) {
        return new Builder(type, ncRecord, quantity);
    }

    public GenealogyType getType() {
        return type;
    }

    public ProductionBatch getSourceBatch() {
        return sourceBatch;
    }

    public ProductionBatch getTargetBatch() {
        return targetBatch;
    }

    public NonConformingRecord getNcRecord() {
        return ncRecord;
    }

    public DispositionOrder getDisposition() {
        return disposition;
    }

    public ReworkSubBatch getReworkSubBatch() {
        return reworkSubBatch;
    }

    public ScrapRecord getScrapRecord() {
        return scrapRecord;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public static final class Builder {
        private final GenealogyType type;
        private final NonConformingRecord ncRecord;
        private final BigDecimal quantity;
        private ProductionBatch sourceBatch;
        private ProductionBatch targetBatch;
        private DispositionOrder disposition;
        private ReworkSubBatch reworkSubBatch;
        private ScrapRecord scrapRecord;

        private Builder(GenealogyType type, NonConformingRecord ncRecord, BigDecimal quantity) {
            this.type = type;
            this.ncRecord = ncRecord;
            this.quantity = quantity;
        }

        public Builder source(ProductionBatch batch) {
            this.sourceBatch = batch;
            return this;
        }

        public Builder target(ProductionBatch batch) {
            this.targetBatch = batch;
            return this;
        }

        public Builder disposition(DispositionOrder disposition) {
            this.disposition = disposition;
            return this;
        }

        public Builder rework(ReworkSubBatch reworkSubBatch) {
            this.reworkSubBatch = reworkSubBatch;
            return this;
        }

        public Builder scrap(ScrapRecord scrapRecord) {
            this.scrapRecord = scrapRecord;
            return this;
        }

        public BatchGenealogyEdge build() {
            return new BatchGenealogyEdge(this);
        }
    }
}
