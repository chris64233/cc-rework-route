package com.chris64233.reworkroute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 纠正记录：已确认的处置不可修改，只能对其追加纠正记录。
 * 纠正记录是追加式的，仅追加、不改写、不删除，保留完整谱系。
 */
@Entity
@Table(name = "correction_record")
public class CorrectionRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private DispositionOrder disposition;

    /** 纠正类型，如 REWORK_INSTRUCTION / CONCESSION_NOTE / ROOT_CAUSE。 */
    @Column(nullable = false, length = 32)
    private String correctionType;

    @Column(nullable = false, length = 2000)
    private String content;

    private String operator;

    protected CorrectionRecord() {
    }

    public CorrectionRecord(DispositionOrder disposition, String correctionType, String content, String operator) {
        this.disposition = disposition;
        this.correctionType = correctionType;
        this.content = content;
        this.operator = operator;
    }

    public DispositionOrder getDisposition() {
        return disposition;
    }

    public String getCorrectionType() {
        return correctionType;
    }

    public String getContent() {
        return content;
    }

    public String getOperator() {
        return operator;
    }
}
