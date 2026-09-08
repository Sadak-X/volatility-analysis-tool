package com.volatility.modules.task.dto;

import java.util.List;

public record TaskBatchActionResponse(
        Integer total,
        List<TaskActionResponse> records
) {
}
