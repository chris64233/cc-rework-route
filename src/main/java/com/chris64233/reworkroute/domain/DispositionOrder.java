package com.chris64233.reworkroute.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 处置单：针对一条不合格记录提出处置决定，将受影响数量拆分为返工、报废、让步接收三部分。
 *
 * <p>businessNo 为对外业务号，是幂等键；contentHash 记录首次提交内容的哈希，
 * 同号重放内容一致时返回原结果，内容不一致返回冲突。已 CONFIRMED 的处置不可修改。</p>
 */
@Entity
@Table(name = "disposition_order")
public class DispositionOrder extends BaseEntity {

    /** 处置业务号，全局唯一，幂等键。 */
    @Column(nullable = false, unique = true)
    private String businessNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private NonConformingRecord ncRecord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DispositionStatus status = DispositionStatus.PENDING;

    /** 首次提交内容（ncNo + 三类数量 + 返工工序序列）的哈希，用于重放冲突检测。 */
    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal reworkQuantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal scrapQuantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal concessionQuantity;

    /** 返工指定工序，按顺序完成，逗号分隔；返工数量为 0 时为空。 */
    @Column(length = 1000)
    private String reworkOperations;

    @OneToMany(mappedBy = "disposition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DispositionLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "disposition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CorrectionRecord> corrections = new ArrayList<>();

    @Version
    private Long version;

    protected DispositionOrder() {
    }

    public DispositionOrder(String businessNo, NonConformingRecord ncRecord, String contentHash,
                            BigDecimal reworkQuantity, BigDecimal scrapQuantity, BigDecimal concessionQuantity,
                            String reworkOperations) {
        this.businessNo = businessNo;
        this.ncRecord = ncRecord;
        this.contentHash = contentHash;
        this.reworkQuantity = reworkQuantity;
        this.scrapQuantity = scrapQuantity;
        this.concessionQuantity = concessionQuantity;
        this.reworkOperations = reworkOperations;
    }

    public BigDecimal totalQuantity() {
        return reworkQuantity.add(scrapQuantity).add(concessionQuantity);
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public NonConformingRecord getNcRecord() {
        return ncRecord;
    }

    public DispositionStatus getStatus() {
        return status;
    }

    public void setStatus(DispositionStatus status) {
        this.status = status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public BigDecimal getReworkQuantity() {
        return reworkQuantity;
    }

    public BigDecimal getScrapQuantity() {
        return scrapQuantity;
    }

    public BigDecimal getConcessionQuantity() {
        return concessionQuantity;
    }

    public String getReworkOperations() {
        return reworkOperations;
    }

    public List<DispositionLine> getLines() {
        return lines;
    }

    public List<CorrectionRecord> getCorrections() {
        return corrections;
    }

    public Long getVersion() {
        return version;
    }
}
