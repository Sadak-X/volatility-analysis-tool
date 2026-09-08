package com.volatility.modules.ai;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import com.volatility.common.exception.BusinessException;
import com.volatility.modules.ai.dto.AiFollowupRequest;
import com.volatility.modules.ai.dto.AiGenerateRequest;
import com.volatility.modules.ai.entity.AiAnalysisReportEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AiController {

    private final AiService aiService;
    private final AiReportPdfService aiReportPdfService;

    public AiController(AiService aiService, AiReportPdfService aiReportPdfService) {
        this.aiService = aiService;
        this.aiReportPdfService = aiReportPdfService;
    }
    @GetMapping("/ai/{taskNo}/latest")
    public ApiResponse<?> latest(@PathVariable String taskNo, HttpServletRequest request) {
        try {
            AiAnalysisReportEntity report = aiService.latestReport(taskNo);
            return ApiResponse.success(aiService.toResponse(report), requestId(request));
        } catch (BusinessException e) {
            // 报告不存在时返回 null，避免前端报错
            return ApiResponse.success(null, requestId(request));
        }
    }

    @PostMapping("/ai/{taskNo}/generate")
    public ApiResponse<?> generate(
            @PathVariable String taskNo,
            @RequestBody(required = false) AiGenerateRequest request,
            HttpServletRequest servletRequest) {
        AiGenerateRequest actual = request == null ? new AiGenerateRequest("PRO") : request;
        return ApiResponse.success(aiService.generate(taskNo, actual), requestId(servletRequest));
    }

    @PostMapping("/ai/{taskNo}/followup")
    public ApiResponse<?> followup(
            @PathVariable String taskNo,
            @Valid @RequestBody AiFollowupRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(aiService.followup(taskNo, request), requestId(servletRequest));
    }

    @GetMapping("/report/{taskNo}/download")
    public ResponseEntity<byte[]> download(@PathVariable String taskNo) {
        String content = aiService.latestReport(taskNo).getFullReportMd();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + taskNo + ".md\"")
                .contentType(MediaType.parseMediaType("text/markdown"))
                .body(content.getBytes());
    }

    @GetMapping("/report/{taskNo}/download/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String taskNo) {
        byte[] content = aiReportPdfService.build(taskNo, aiService.latestReport(taskNo));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + taskNo + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
