package com.volatility.modules.ai.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_analysis_report")
public class AiAnalysisReportEntity extends BaseEntity {

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 16)
    private String stockCode;

    @Column(nullable = false, length = 16)
    private String analysisMode;

    @Column(nullable = false, length = 64)
    private String promptHash;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String inputJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String summaryText;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String fullReportMd;

    @Column(length = 500)
    private String riskDisclaimer;
}
