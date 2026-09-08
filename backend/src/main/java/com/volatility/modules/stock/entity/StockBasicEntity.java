package com.volatility.modules.stock.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stock_basic")
public class StockBasicEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 16)
    private String stockCode;

    @Column(nullable = false, length = 64)
    private String stockName;

    @Column(length = 64)
    private String industryName;

    @Column(length = 16)
    private String marketType;

    @Column(precision = 18, scale = 4)
    private BigDecimal latestPrice;

    @Column(precision = 10, scale = 4)
    private BigDecimal changeRate;

    @Column(precision = 20, scale = 2)
    private BigDecimal volume;

    @Column(precision = 12, scale = 6)
    private BigDecimal historicalVolatility;

    @Column(precision = 12, scale = 6)
    private BigDecimal impliedVolatility;

    @Column(precision = 12, scale = 6)
    private BigDecimal predictedVolatility;

    @Column(precision = 8, scale = 2)
    private BigDecimal totalScore;

    @Column(length = 16)
    private String riskLevel;
}
