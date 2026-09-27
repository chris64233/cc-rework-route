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
 * 纠正记录：处置确认冻结后，只能以追加方式记录后续纠正措施，
 * 不得修改处置内容，从而保留完整谱系。
 */
@Entity
@Table(name = "correction_entry")
public class CorrectionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disposition_id", nullable = false)
    private Disposition disposition;

    @Column(nullable = false, length = 32)
    private String actionCode;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false)
    private Instant createdAt;

    protected CorrectionEntry() {
    }

    public CorrectionEntry(Disposition disposition, String actionCode, String detail, String operator) {
        this.disposition = disposition;
        this.actionCode = actionCode;
        this.detail = detail;
        this.operator = operator;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public String getActionCode() {
        return actionCode;
    }

    public String getDetail() {
        return detail;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
