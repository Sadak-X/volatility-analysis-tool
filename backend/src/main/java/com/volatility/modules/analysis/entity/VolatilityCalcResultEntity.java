package com.volatility.modules.analysis.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "volatility_calc_result")
public class VolatilityCalcResultEntity extends BaseEntity {

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 16)
    private String stockCode;

    @Column(nullable = false)
    private LocalDate calcDate;

    @Column(precision = 12, scale = 6)
    private BigDecimal yzVolatility;

    @Column(precision = 12, scale = 6)
    private BigDecimal impliedVolatility;

    @Column(nullable = false)
    private Integer windowSize;

    @Column(nullable = false, length = 16)
    private String calcStatus;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;
}
