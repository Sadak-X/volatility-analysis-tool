package com.volatility.modules.assessment.entity;

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
@Table(name = "volatility_assess_result")
public class VolatilityAssessResultEntity extends BaseEntity {

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 16)
    private String stockCode;

    @Column(precision = 8, scale = 2)
    private BigDecimal scoreTotal;

    @Column(precision = 8, scale = 2)
    private BigDecimal scoreStability;

    @Column(precision = 8, scale = 2)
    private BigDecimal scoreRisk;

    @Column(length = 16)
    private String riskLevel;

    @Column(length = 64)
    private String qualitativeLabel;

    @Column(precision = 12, scale = 6)
    private BigDecimal industryAvgVol;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String donchianJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;
}
