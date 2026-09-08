package com.volatility.modules.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TaskCreateRequest(
        @NotBlank(message = "任务类型不能为空") String taskType,
        @NotBlank(message = "数据源不能为空") String dataSourceType,
        @NotBlank(message = "股票模式不能为空") String stockMode,
        List<String> stockCodes,
        @NotNull(message = "开始日期不能为空") LocalDate dateStart,
        @NotNull(message = "结束日期不能为空") LocalDate dateEnd,
        List<String> chartSpanModes,
        BigDecimal confidenceLevel,
        Boolean useDefaultParams,
        String timeGranularity,
        Integer windowSize,
        String forecastType,
        String fileId,
        String uploadedFilePath
) {
}
