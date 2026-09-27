package com.chris64233.reworkroute.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 处置决定：把不合格记录的受影响数量拆分为返工 reworkQty、报废 scrapQty、
 * 让步接收 concessionQty 三部分。三者之和必须等于受影响数量，确认时原子校验并落地。
 *
 * 业务号 businessNo 是幂等键：相同内容重放返回原处置；内容不同报冲突。
 * 一旦 CONFIRMED 即冻结，只能通过 corrections 追加纠正记录，保留完整谱系。
 */
@Entity
@Table(name = "disposition")
public class Disposition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户端提供的处置业务号，全局唯一，作为重放幂等键。 */
    @Column(nullable = false, unique = true)
    private String businessNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "nc_id", nullable = false)
    private NonconformingRecord nc;

    @Column(nullable = false)
    private int reworkQty;

    @Column(nullable = false)
    private int scrapQty;

    @Column(nullable = false)
    private int concessionQty;

    /**
     * 返工必须依次完成的工序代码（有序）。仅在 reworkQty > 0 时需要，
     * 全部完成后才允许提交复验。
     */
    @OrderColumn(name = "step_index")
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "disposition_id")
    private List<ReworkOperation> requiredOperations = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DispositionStatus status;

    /** 确认后生成的返工子批次（reworkQty > 0 时存在）。 */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rework_sub_batch_id", unique = true)
    private ReworkSubBatch reworkSubBatch;

    /** 确认后生成的报废记录（scrapQty > 0 时存在）。 */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "scrap_record_id", unique = true)
    private ScrapRecord scrapRecord;

    @OneToMany(mappedBy = "disposition", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<CorrectionEntry> corrections = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    private Instant confirmedAt;

    @Version
    private long version;

    protected Disposition() {
    }

    public Disposition(String businessNo, NonconformingRecord nc, int reworkQty, int scrapQty,
                       int concessionQty, List<String> requiredOperations) {
        this.businessNo = businessNo;
        this.nc = nc;
        this.reworkQty = reworkQty;
        this.scrapQty = scrapQty;
        this.concessionQty = concessionQty;
        requiredOperations.stream().map(ReworkOperation::new).forEach(this.requiredOperations::add);
        this.status = DispositionStatus.DRAFT;
        this.createdAt = Instant.now();
    }

    public int totalQuantity() {
        return reworkQty + scrapQty + concessionQty;
    }

    public boolean contentEquals(Long otherNcId, int otherRework, int otherScrap,
                                 int otherConcession, List<String> otherOperations) {
        if (!this.nc.getId().equals(otherNcId)
                || this.reworkQty != otherRework
                || this.scrapQty != otherScrap
                || this.concessionQty != otherConcession) {
            return false;
        }
        List<String> mine = requiredOperations.stream().map(ReworkOperation::getOperationCode).toList();
        return mine.equals(otherOperations);
    }

    public void confirm(ReworkSubBatch subBatch, ScrapRecord scrap) {
        if (status == DispositionStatus.CONFIRMED) {
            throw new IllegalStateException("disposition already confirmed: " + businessNo);
        }
        this.reworkSubBatch = subBatch;
        this.scrapRecord = scrap;
        this.status = DispositionStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public void addCorrection(CorrectionEntry entry) {
        this.corrections.add(entry);
    }

    public Long getId() {
        return id;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public NonconformingRecord getNc() {
        return nc;
    }

    public int getReworkQty() {
        return reworkQty;
    }

    public int getScrapQty() {
        return scrapQty;
    }

    public int getConcessionQty() {
        return concessionQty;
    }

    public List<ReworkOperation> getRequiredOperations() {
        return requiredOperations;
    }

    public DispositionStatus getStatus() {
        return status;
    }

    public ReworkSubBatch getReworkSubBatch() {
        return reworkSubBatch;
    }

    public ScrapRecord getScrapRecord() {
        return scrapRecord;
    }

    public List<CorrectionEntry> getCorrections() {
        return corrections;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public long getVersion() {
        return version;
    }
}
