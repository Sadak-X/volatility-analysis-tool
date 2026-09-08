package com.volatility.modules.task.dto;

public record TaskActionResponse(
        String taskNo,
        String taskStatus,
        Integer errorCount,
        Boolean errorFileAvailable,
        String errorDownloadUrl
) {
}
