package com.volatility.modules.dashboard;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import com.volatility.modules.dashboard.dto.RankSnapshotDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/high-volatility")
    public ApiResponse<List<RankSnapshotDto>> highVolatility(
            @RequestParam(defaultValue = "WEEK") String scope,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return ApiResponse.success(dashboardService.highVolatility(scope, limit), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
