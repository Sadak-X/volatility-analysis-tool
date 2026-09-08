package com.volatility.modules.task;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import com.volatility.modules.task.dto.TaskActionResponse;
import com.volatility.modules.task.dto.TaskBatchActionResponse;
import com.volatility.modules.task.dto.TaskCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }


    @DeleteMapping("/{taskNo}")
    public ApiResponse<TaskActionResponse> delete(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(taskService.delete(taskNo), requestId(request));
    }

    @PostMapping("/batch-delete")
    public ApiResponse<TaskBatchActionResponse> batchDelete(@RequestBody List<String> taskNos, HttpServletRequest request) {
        return ApiResponse.success(taskService.deleteBatch(taskNos), requestId(request));
    }

    @PostMapping("/create")
    public ApiResponse<TaskActionResponse> create(@Valid @RequestBody TaskCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(taskService.create(request), requestId(servletRequest));
    }

    @PostMapping("/batch-create")
    public ApiResponse<TaskBatchActionResponse> batchCreate(@Valid @RequestBody TaskCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(taskService.createBatch(request), requestId(servletRequest));
    }

    @GetMapping("/page")
    public ApiResponse<?> page(
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(taskService.page(taskStatus, stockCode, taskType, pageNo, pageSize), requestId(request));
    }

    @GetMapping("/{taskNo}")
    public ApiResponse<?> detail(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(taskService.detail(taskNo), requestId(request));
    }

    @PostMapping("/{taskNo}/cancel")
    public ApiResponse<TaskActionResponse> cancel(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(taskService.cancel(taskNo), requestId(request));
    }

    @PostMapping("/{taskNo}/rerun")
    public ApiResponse<TaskActionResponse> rerun(@PathVariable String taskNo, HttpServletRequest request) {
        return ApiResponse.success(taskService.rerun(taskNo), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
