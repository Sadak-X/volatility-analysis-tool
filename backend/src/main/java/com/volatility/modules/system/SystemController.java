package com.volatility.modules.system;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.AppProperties;
import com.volatility.common.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final AppProperties appProperties;

    public SystemController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping("/dict/confidence-level")
    public ApiResponse<List<Map<String, Object>>> confidenceLevels(HttpServletRequest request) {
        List<Map<String, Object>> data = List.of(
                Map.of("label", "90%", "value", 0.90),
                Map.of("label", "95%", "value", 0.95),
                Map.of("label", "99%", "value", 0.99)
        );
        return ApiResponse.success(data, requestId(request));
    }

    @GetMapping("/system/config")
    public ApiResponse<Map<String, Object>> config(HttpServletRequest request) {
        Map<String, Object> data = Map.of(
                "defaultChartSpanModes", appProperties.getDefaultChartSpans(),
                "defaultConfidenceLevel", appProperties.getDefaultConfidenceLevel(),
                "defaultWindowSize", 20,
                "defaultForecastType", "MONTH"
        );
        return ApiResponse.success(data, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
