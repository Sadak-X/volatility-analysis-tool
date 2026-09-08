package com.volatility.modules.task;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import com.volatility.modules.file.FileStorageService;
import com.volatility.modules.file.dto.FileUploadResponse;
import com.volatility.modules.task.dto.ExcelImportRequest;
import com.volatility.modules.task.dto.TaskActionResponse;
import com.volatility.modules.task.dto.TaskCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/data")
public class DataImportController {

    private final TaskService taskService;
    private final FileStorageService fileStorageService;

    public DataImportController(TaskService taskService, FileStorageService fileStorageService) {
        this.taskService = taskService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(fileStorageService.uploadExcel(file), requestId(servletRequest));
    }

    @PostMapping("/import/market")
    public ApiResponse<TaskActionResponse> importMarket(@Valid @RequestBody TaskCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(taskService.create(request), requestId(servletRequest));
    }

    @PostMapping("/import/excel")
    public ApiResponse<TaskActionResponse> importExcel(@Valid @RequestBody ExcelImportRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(taskService.createExcelTask(request), requestId(servletRequest));
    }
    @PostMapping("/import/stocks/excel")
    public ApiResponse<?> importStocksFromExcel(@Valid @RequestBody ExcelImportRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(taskService.importStocksFromExcel(request), requestId(servletRequest));
    }
    @PostMapping("/import/stocks")
    public ApiResponse<?> importStocks(@Valid @RequestBody TaskCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(taskService.importStocks(request), requestId(servletRequest));
    }

    @GetMapping("/import/{taskNo}/errors/download")
    public ResponseEntity<Resource> downloadErrorFile(@PathVariable String taskNo) throws IOException {
        Path path = taskService.getExcelErrorFilePath(taskNo);
        Resource resource = new FileSystemResource(path);
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(resource);
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
