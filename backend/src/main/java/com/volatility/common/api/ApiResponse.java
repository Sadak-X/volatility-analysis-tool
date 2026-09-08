package com.volatility.common.api;

import java.time.LocalDateTime;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String requestId,
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(0, "success", data, requestId, LocalDateTime.now());
    }

    public static ApiResponse<Void> success(String requestId) {
        return new ApiResponse<>(0, "success", null, requestId, LocalDateTime.now());
    }

    public static ApiResponse<Void> failure(int code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId, LocalDateTime.now());
    }
}
