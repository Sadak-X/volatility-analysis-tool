package com.volatility.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RankSnapshotDto(
        LocalDate bizDate,
        String scope,
        String stockCode,
        String stockName,
        BigDecimal predVolatility,
        BigDecimal changeRate,
        BigDecimal lastPrice,
        String riskLevel,
        Integer rankNo,
        LocalDateTime snapshotTime
) {
}
