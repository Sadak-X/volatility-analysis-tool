package com.volatility.modules.analysis;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/{taskNo}/overview")
    public ApiResponse<?> overview(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(analysisService.overview(taskNo), requestId(request));
    }

    @GetMapping("/{taskNo}/trend")
    public ApiResponse<?> trend(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(analysisService.trend(taskNo), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
