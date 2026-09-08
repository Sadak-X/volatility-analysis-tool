package com.volatility.modules.file.dto;

public record FileUploadResponse(
        String fileId,
        String originalFileName,
        Long fileSize,
        String contentType
) {
}
