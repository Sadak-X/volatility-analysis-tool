package com.volatility.modules.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExcelImportRequest(
        @NotBlank(message = "文件ID不能为空") String fileId,
        @NotBlank(message = "任务类型不能为空") String taskType,
        @NotNull(message = "开始日期不能为空") LocalDate dateStart,
        @NotNull(message = "结束日期不能为空") LocalDate dateEnd,
        List<String> chartSpanModes,
        BigDecimal confidenceLevel,
        Boolean useDefaultParams,
        String timeGranularity,
        Integer windowSize,
        String forecastType
) {
}
