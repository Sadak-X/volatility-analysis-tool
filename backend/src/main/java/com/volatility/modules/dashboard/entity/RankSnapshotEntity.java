package com.volatility.modules.dashboard.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_volatility_rank_snapshot")
public class RankSnapshotEntity extends BaseEntity {

    @Column(nullable = false)
    private LocalDate bizDate;

    @Column(nullable = false, length = 16)
    private String sortScope;

    @Column(nullable = false, length = 16)
    private String stockCode;

    @Column(nullable = false, length = 64)
    private String stockName;

    @Column(precision = 12, scale = 6)
    private BigDecimal predVolatility;

    @Column(precision = 10, scale = 4)
    private BigDecimal changeRate;

    @Column(precision = 18, scale = 4)
    private BigDecimal lastPrice;

    @Column(length = 16)
    private String riskLevel;

    @Column(nullable = false)
    private Integer rankNo;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;
}
