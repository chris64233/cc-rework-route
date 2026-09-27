package com.chris64233.reworkroute.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 库存（数量）移动台账。所有不合格数量的每次流向都生成一条移动记录，
 * 用于核对“同一原始数量不能被重复消费”以及去向查询。
 */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductionBatch batch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "nc_id", nullable = false)
    private NonconformingRecord nc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MovementType type;

    /** 有符号数量：减少可用库存为负、增加可用库存/在途为正，含义由 type 决定。 */
    @Column(nullable = false)
    private int quantity;

    @Column(length = 64)
    private String refNo;

    @Column(nullable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(ProductionBatch batch, NonconformingRecord nc, MovementType type, int quantity,
                         String refNo) {
        this.batch = batch;
        this.nc = nc;
        this.type = type;
        this.quantity = quantity;
        this.refNo = refNo;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ProductionBatch getBatch() {
        return batch;
    }

    public NonconformingRecord getNc() {
        return nc;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getRefNo() {
        return refNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
