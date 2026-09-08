package com.volatility.modules.assessment;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/{taskNo}/detail")
    public ApiResponse<?> detail(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(assessmentService.detail(taskNo), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
