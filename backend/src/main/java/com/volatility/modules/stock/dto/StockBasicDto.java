package com.volatility.modules.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockBasicDto(
        String stockCode,
        String stockName,
        String industryName,
        String marketType,
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
