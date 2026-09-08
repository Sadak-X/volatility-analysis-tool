package com.volatility.modules.forecast.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "volatility_forecast_result")
public class VolatilityForecastResultEntity extends BaseEntity {

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 16)
    private String stockCode;

    @Column(nullable = false, length = 8)
    private String forecastType;

    @Column(precision = 12, scale = 6)
    private BigDecimal predictVolatility;

    @Column(precision = 12, scale = 6)
    private BigDecimal ciLower;

    @Column(precision = 12, scale = 6)
    private BigDecimal ciUpper;

    @Column(precision = 12, scale = 6)
    private BigDecimal serValue;

    @Column(length = 16)
    private String riskLevel;

    @Column(length = 64)
    private String modelName;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;
}
