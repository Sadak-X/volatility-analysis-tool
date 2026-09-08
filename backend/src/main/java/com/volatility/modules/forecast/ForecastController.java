package com.volatility.modules.forecast;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/page")
    public ApiResponse<?> page(
            @RequestParam(required = false) String stockCode,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(forecastService.page(stockCode, pageNo, pageSize), requestId(request));
    }

    @GetMapping("/{taskNo}/detail")
    public ApiResponse<?> detail(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(forecastService.detail(taskNo), requestId(request));
    }

    @GetMapping("/{taskNo}/items")
    public ApiResponse<?> items(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(forecastService.items(taskNo), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
