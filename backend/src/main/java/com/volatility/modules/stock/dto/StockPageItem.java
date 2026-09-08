package com.volatility.modules.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockPageItem(
        String stockCode,
        String stockName,
        String industryName,
        BigDecimal latestPrice,
        BigDecimal changeRate,
        BigDecimal volume,
        BigDecimal historicalVolatility,
        BigDecimal impliedVolatility,
        BigDecimal predictedVolatility,
        BigDecimal totalScore,
        String riskLevel,
        LocalDateTime updateTime
) {
}
