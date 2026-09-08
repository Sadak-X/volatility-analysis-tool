package com.volatility.modules.forecast.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "volatility_forecast_detail")
public class VolatilityForecastDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long forecastResultId;

    @Column(nullable = false)
    private LocalDate forecastDate;

    @Column(precision = 12, scale = 6)
    private BigDecimal predValue;

    @Column(precision = 12, scale = 6)
    private BigDecimal ciLower;

    @Column(precision = 12, scale = 6)
    private BigDecimal ciUpper;

    @Column(precision = 12, scale = 6)
    private BigDecimal serValue;

    @Column(nullable = false)
    private Integer rankNo;
}
