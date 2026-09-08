package com.volatility.modules.task;

import com.volatility.common.config.AppProperties;
import com.volatility.common.enums.TaskStatus;
import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.AiNarrativeService;
import com.volatility.modules.analysis.entity.VolatilityCalcResultEntity;
import com.volatility.modules.analysis.repository.VolatilityCalcResultRepository;
import com.volatility.modules.assessment.entity.VolatilityAssessResultEntity;
import com.volatility.modules.assessment.repository.VolatilityAssessResultRepository;
import com.volatility.modules.auth.AuthService;
import com.volatility.modules.dashboard.entity.RankSnapshotEntity;
import com.volatility.modules.dashboard.repository.RankSnapshotRepository;
import com.volatility.modules.file.FileStorageService;
import com.volatility.modules.file.entity.DataSourceFileEntity;
import com.volatility.modules.forecast.entity.VolatilityForecastDetailEntity;
import com.volatility.modules.forecast.entity.VolatilityForecastResultEntity;
import com.volatility.modules.forecast.repository.VolatilityForecastDetailRepository;
import com.volatility.modules.forecast.repository.VolatilityForecastResultRepository;
import com.volatility.modules.stock.entity.StockBasicEntity;
import com.volatility.modules.stock.repository.StockBasicRepository;
import com.volatility.modules.task.dto.TaskActionResponse;
import com.volatility.modules.task.dto.TaskBatchActionResponse;
import com.volatility.modules.task.dto.ExcelImportRequest;
import com.volatility.modules.task.dto.TaskCreateRequest;
import com.volatility.modules.task.entity.TaskMainEntity;
import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
import com.volatility.modules.task.entity.TaskStepLogEntity;
import com.volatility.modules.task.repository.TaskMainRepository;
import com.volatility.modules.task.repository.TaskParamSnapshotRepository;
import com.volatility.modules.task.repository.TaskStepLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final TaskMainRepository taskMainRepository;
    private final TaskParamSnapshotRepository taskParamSnapshotRepository;
    private final TaskStepLogRepository taskStepLogRepository;
    private final StockBasicRepository stockBasicRepository;
    private final VolatilityCalcResultRepository calcResultRepository;
    private final VolatilityAssessResultRepository assessResultRepository;
    private final VolatilityForecastResultRepository forecastResultRepository;
    private final VolatilityForecastDetailRepository forecastDetailRepository;
    private final RankSnapshotRepository rankSnapshotRepository;
    private final FileStorageService fileStorageService;
    private final JsonUtils jsonUtils;
    private final AppProperties appProperties;
    private final PythonClient pythonClient;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

    private final AiNarrativeService aiNarrativeService;

    public TaskService(
            TaskMainRepository taskMainRepository,
            TaskParamSnapshotRepository taskParamSnapshotRepository,
            TaskStepLogRepository taskStepLogRepository,
            StockBasicRepository stockBasicRepository,
            VolatilityCalcResultRepository calcResultRepository,
            VolatilityAssessResultRepository assessResultRepository,
            VolatilityForecastResultRepository forecastResultRepository,
            VolatilityForecastDetailRepository forecastDetailRepository,
            RankSnapshotRepository rankSnapshotRepository,
            FileStorageService fileStorageService,
            JsonUtils jsonUtils,
            AppProperties appProperties,
            PythonClient pythonClient,
            AuthService authService,
            ApplicationEventPublisher eventPublisher,
            AiNarrativeService aiNarrativeService) {
        this.taskMainRepository = taskMainRepository;
        this.taskParamSnapshotRepository = taskParamSnapshotRepository;
        this.taskStepLogRepository = taskStepLogRepository;
        this.stockBasicRepository = stockBasicRepository;
        this.calcResultRepository = calcResultRepository;
        this.assessResultRepository = assessResultRepository;
        this.forecastResultRepository = forecastResultRepository;
        this.forecastDetailRepository = forecastDetailRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.fileStorageService = fileStorageService;
        this.jsonUtils = jsonUtils;
        this.appProperties = appProperties;
        this.pythonClient = pythonClient;
        this.authService = authService;
        this.eventPublisher = eventPublisher;
        this.aiNarrativeService = aiNarrativeService;
    }

    public TaskActionResponse delete(String taskNo) {
        TaskMainEntity task = findTask(taskNo);
        deleteTaskAndRelatedData(task.getId());
        return new TaskActionResponse(taskNo, "DELETED", null, null, null);
    }

    public Map<String, Object> importStocks(TaskCreateRequest request) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataSourceType", request.dataSourceType().toUpperCase());
        payload.put("stockCodes", request.stockCodes() == null ? List.of() : request.stockCodes());
        payload.put("dateStart", request.dateStart().toString());
        payload.put("dateEnd", request.dateEnd().toString());
        payload.put("taskType", "ANALYSIS");
        payload.put("stockMode", "SINGLE");
        payload.put("chartSpanModes", List.of("YEAR", "MONTH", "WEEK"));
        payload.put("confidenceLevel", BigDecimal.valueOf(0.90));

        Map<String, Object> ingest = pythonClient.post("/py-api/ingest/market", payload);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) ingest.getOrDefault("items", List.of());
        for (Map<String, Object> item : items) {
            String stockCode = String.valueOf(item.get("stockCode"));
            StockBasicEntity stock = stockBasicRepository.findByStockCode(stockCode).orElseGet(StockBasicEntity::new);
            stock.setStockCode(stockCode);
            stock.setStockName(String.valueOf(item.getOrDefault("stockName", stockCode)));
            stock.setIndustryName(String.valueOf(item.getOrDefault("industryName", "未分类")));
            stock.setMarketType(String.valueOf(item.getOrDefault("marketType", "CN")));
            stock.setLatestPrice(decimal(item.get("latestPrice")));
            stock.setChangeRate(decimal(item.get("changeRate")));
            stock.setVolume(decimal(item.get("volume")));
            stockBasicRepository.save(stock);
        }

        return Map.of(
                "importedCount", items.size(),
                "stockCodes", request.stockCodes()
        );
    }

    public Map<String, Object> importStocksFromExcel(ExcelImportRequest request) {
        DataSourceFileEntity file = fileStorageService.getByFileNo(request.fileId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataSourceType", "EXCEL");
        payload.put("uploadedFilePath", file.getFilePath());
        payload.put("stockCodes", List.of());
        payload.put("dateStart", request.dateStart().toString());
        payload.put("dateEnd", request.dateEnd().toString());
        payload.put("taskType", "ANALYSIS");
        payload.put("stockMode", "SINGLE");

        Map<String, Object> result = pythonClient.post("/py-api/ingest/excel", payload);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOrDefault("items", List.of());

        for (Map<String, Object> item : items) {
            String stockCode = String.valueOf(item.get("stockCode"));
            StockBasicEntity stock = stockBasicRepository.findByStockCode(stockCode).orElseGet(StockBasicEntity::new);
            stock.setStockCode(stockCode);
            stock.setStockName(String.valueOf(item.getOrDefault("stockName", stockCode)));
            stock.setIndustryName(String.valueOf(item.getOrDefault("industryName", "未分类")));
            stock.setMarketType(String.valueOf(item.getOrDefault("marketType", "CN")));
            stock.setLatestPrice(decimal(item.get("latestPrice")));
            stock.setChangeRate(decimal(item.get("changeRate")));
            stock.setVolume(decimal(item.get("volume")));
            stockBasicRepository.save(stock);
        }

        Integer errorCount = result.get("errorCount") instanceof Number n ? n.intValue() : 0;
        String errorFilePath = (String) result.get("errorFilePath");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("importedCount", items.size());
        response.put("errorCount", errorCount);
        response.put("errorFilePath", errorFilePath);
        response.put("stockCodes", items.stream().map(item -> String.valueOf(item.get("stockCode"))).toList());
        return response;
    }
    public TaskBatchActionResponse deleteBatch(List<String> taskNos) {
        List<TaskActionResponse> records = new ArrayList<>();
        for (String taskNo : taskNos) {
            try {
                records.add(delete(taskNo));
            } catch (Exception ex) {
                records.add(new TaskActionResponse(taskNo, "FAILED", null, null, null));
            }
        }
        return new TaskBatchActionResponse(records.size(), records);
    }

    private void deleteTaskAndRelatedData(Long taskId) {
        forecastDetailRepository.deleteByTaskId(taskId);
        forecastResultRepository.deleteByTaskId(taskId);
        assessResultRepository.deleteByTaskId(taskId);
        calcResultRepository.deleteByTaskId(taskId);
        taskStepLogRepository.deleteByTaskId(taskId);
        taskParamSnapshotRepository.deleteByTaskId(taskId);
        taskMainRepository.deleteById(taskId);
    }

    public TaskActionResponse create(TaskCreateRequest request) {
        TaskMainEntity task = buildTask(request);
        taskMainRepository.save(task);
        taskParamSnapshotRepository.save(buildSnapshot(task, request));
        dispatchProcess(task.getTaskNo());
        return buildActionResponse(task);
    }

    public TaskBatchActionResponse createBatch(TaskCreateRequest request) {
        List<String> stockCodes = request.stockCodes() == null
                ? List.of()
                : request.stockCodes().stream()
                        .filter(code -> code != null && !code.isBlank())
                        .distinct()
                        .toList();
        if (stockCodes.isEmpty()) {
            throw new BusinessException(1003, "璇峰厛閫夋嫨鑲＄エ");
        }
        TaskActionResponse record = create(new TaskCreateRequest(
                request.taskType(),
                request.dataSourceType(),
                stockCodes.size() > 1 ? "MULTI" : request.stockMode(),
                stockCodes,
                request.dateStart(),
                request.dateEnd(),
                request.chartSpanModes(),
                request.confidenceLevel(),
                request.useDefaultParams(),
                request.timeGranularity(),
                request.windowSize(),
                request.forecastType(),
                request.fileId(),
                request.uploadedFilePath()
        ));
        return new TaskBatchActionResponse(1, List.of(record));
    }

    public TaskActionResponse createExcelTask(ExcelImportRequest request) {
        DataSourceFileEntity file = fileStorageService.getByFileNo(request.fileId());
        TaskCreateRequest taskCreateRequest = new TaskCreateRequest(
                request.taskType(),
                "EXCEL",
                "SINGLE",
                List.of(),
                request.dateStart(),
                request.dateEnd(),
                request.chartSpanModes(),
                request.confidenceLevel(),
                request.useDefaultParams(),
                request.timeGranularity(),
                request.windowSize(),
                request.forecastType(),
                file.getFileNo(),
                file.getFilePath()
        );
        return create(taskCreateRequest);
    }

    public TaskActionResponse rerun(String taskNo) {
        TaskMainEntity task = findTask(taskNo);
        task.setTaskStatus(TaskStatus.PENDING.name());
        task.setProgress(0);
        task.setErrorMsg(null);
        task.setResultSummary(null);
        task.setStartTime(null);
        task.setEndTime(null);
        taskMainRepository.save(task);
        dispatchProcess(taskNo);
        return buildActionResponse(task);
    }

    public TaskActionResponse cancel(String taskNo) {
        TaskMainEntity task = findTask(taskNo);
        if (!(TaskStatus.DRAFT.name().equals(task.getTaskStatus()) || TaskStatus.PENDING.name().equals(task.getTaskStatus()))) {
            throw new BusinessException(1003, "当前状态不允许取消");
        }
        task.setTaskStatus(TaskStatus.ARCHIVED.name());
        task.setEndTime(LocalDateTime.now());
        taskMainRepository.save(task);
        addLog(task.getId(), "CANCEL", "SUCCESS", "任务已取消", Map.of());
        return buildActionResponse(task);
    }

    public Map<String, Object> detail(String taskNo) {
        TaskMainEntity task = findTask(taskNo);
        TaskParamSnapshotEntity snapshot = findSnapshot(task.getId());
        List<TaskStepLogEntity> steps = taskStepLogRepository.findByTaskIdOrderByCreateTimeAsc(task.getId());
        Map<String, Object> taskMap = new LinkedHashMap<>();
        taskMap.put("taskNo", task.getTaskNo());
        taskMap.put("taskType", task.getTaskType());
        taskMap.put("taskStatus", task.getTaskStatus());
        taskMap.put("stockMode", task.getStockMode());
        taskMap.put("stockCount", task.getStockCount());
        taskMap.put("progress", task.getProgress());
        taskMap.put("stockCode", task.getStockCode());
        taskMap.put("stockName", task.getStockName());
        taskMap.put("resultSummary", task.getResultSummary());
        taskMap.put("errorMsg", task.getErrorMsg());
        taskMap.put("startTime", task.getStartTime());
        taskMap.put("endTime", task.getEndTime());
        taskMap.put("createTime", task.getCreateTime());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", taskMap);
        data.put("snapshot", jsonUtils.toMap(snapshot.getParamJson()));
        data.put("steps", steps.stream().map(log -> Map.of(
                "stepCode", log.getStepCode(),
                "stepStatus", log.getStepStatus(),
                "messageText", log.getMessageText(),
                "detail", jsonUtils.toMap(log.getDetailJson()),
                "createTime", log.getCreateTime()
        )).toList());
        return data;
    }

    public Map<String, Object> page(String taskStatus, String stockCode, String taskType, int pageNo, int pageSize) {
        String taskStatusFilter = blank(taskStatus);
        String stockCodeFilter = blank(stockCode);
        String taskTypeFilter = blank(taskType);
        Page<TaskMainEntity> page = taskMainRepository.findByTaskStatusContainingAndStockCodeContainingAndTaskTypeContaining(
                taskStatusFilter,
                stockCodeFilter,
                taskTypeFilter,
                PageRequest.of(
                        Math.max(pageNo - 1, 0),
                        pageSize,
                        Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"))));
        return Map.of(
                "records", page.getContent().stream().map(task -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("taskNo", task.getTaskNo());
                    item.put("taskType", task.getTaskType());
                    item.put("taskStatus", task.getTaskStatus());
                    item.put("stockCode", task.getStockCode());
                    item.put("stockName", task.getStockName());
                    item.put("stockCount", task.getStockCount());
                    item.put("progress", task.getProgress());
                    item.put("resultSummary", task.getResultSummary());
                    item.put("createTime", task.getCreateTime());
                    item.put("endTime", task.getEndTime());
                    return item;
                }).toList(),
                "total", page.getTotalElements(),
                "statusSummary", buildStatusSummary(taskStatusFilter, stockCodeFilter, taskTypeFilter, page.getTotalElements()),
                "pageNo", pageNo,
                "pageSize", pageSize
        );
    }

    private Map<String, Object> buildStatusSummary(String taskStatus, String stockCode, String taskType, long total) {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        taskMainRepository.countByStatus(taskStatus, stockCode, taskType)
                .forEach(item -> statusCounts.put(item.getStatus(), item.getCount()));

        Map<String, Long> groups = new LinkedHashMap<>();
        long running = countStatuses(statusCounts, List.of(
                TaskStatus.FETCHING,
                TaskStatus.CLEANING,
                TaskStatus.CALCULATED,
                TaskStatus.ASSESSED));
        long completed = countStatuses(statusCounts, List.of(TaskStatus.FORECASTED, TaskStatus.AI_DONE));
        long failed = countStatuses(statusCounts, List.of(TaskStatus.FAILED));
        long pending = countStatuses(statusCounts, List.of(TaskStatus.PENDING));
        long groupedTotal = running + completed + failed + pending;

        groups.put("running", running);
        groups.put("completed", completed);
        groups.put("failed", failed);
        groups.put("pending", pending);
        groups.put("other", Math.max(total - groupedTotal, 0));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("groups", groups);
        summary.put("statuses", statusCounts);
        return summary;
    }

    private long countStatuses(Map<String, Long> statusCounts, List<TaskStatus> statuses) {
        return statuses.stream()
                .map(TaskStatus::name)
                .mapToLong(status -> statusCounts.getOrDefault(status, 0L))
                .sum();
    }

    public TaskMainEntity findTask(String taskNo) {
        return taskMainRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException(1002, "任务不存在"));
    }

    public TaskParamSnapshotEntity findSnapshot(Long taskId) {
        return taskParamSnapshotRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(1002, "任务快照不存在"));
    }

    public void process(String taskNo) {
        TaskMainEntity task = findTask(taskNo);
        TaskParamSnapshotEntity snapshot = findSnapshot(task.getId());
        Map<String, Object> payload = buildPythonPayload(task, snapshot);
        try {
            updateTask(task, TaskStatus.FETCHING, 10, null);
            addLog(task.getId(), "FETCHING", "RUNNING", "开始获取行情数据", payload);
            Map<String, Object> ingest = pythonClient.post(resolveIngestPath(snapshot.getDataSourceType()), payload);
            addLog(task.getId(), "FETCHING", "SUCCESS", "行情获取完成", ingest);
            syncStocksFromIngest(task, ingest);

            updateTask(task, TaskStatus.CLEANING, 30, null);
            addLog(task.getId(), "CLEANING", "SUCCESS", "清洗与标准化完成", ingest);

            Map<String, Object> calc = pythonClient.post("/py-api/calc/volatility", payload);
            Map<String, Object> assess = pythonClient.post("/py-api/assess/run", payload);
            Map<String, Object> forecast = pythonClient.post("/py-api/forecast/run", payload);

            cleanupExistingResults(task.getId());

            persistCalc(task, snapshot, calc);
            updateTask(task, TaskStatus.CALCULATED, 55, "历史波动率与隐含波动率计算完成");

            persistAssess(task, assess);
            updateTask(task, TaskStatus.ASSESSED, 75, "综合评估完成");

            persistForecast(task, snapshot, forecast);
            updateTask(task, TaskStatus.FORECASTED, 100, "预测结果已生成");
            refreshRankSnapshots();

            CompletableFuture.runAsync(() -> {
                try {

                    List<Map<String, Object>> calcItems = (List<Map<String, Object>>) calc.get("items");
                    if (calcItems != null && !calcItems.isEmpty()) {
                        Map<String, Object> firstCalc = calcItems.get(0);
                        Map<String, Object> analysisData = new LinkedHashMap<>();
                        analysisData.put("historicalVolatility", firstCalc.get("yzVolatility"));
                        analysisData.put("impliedVolatility", firstCalc.get("impliedVolatility"));
                        analysisData.put("impliedVolatilityMethod", firstCalc.get("impliedVolatilityMethod"));
                        aiNarrativeService.generateAnalysisConclusion(analysisData);
                    }

                    List<Map<String, Object>> assessItems = (List<Map<String, Object>>) assess.get("items");
                    if (assessItems != null && !assessItems.isEmpty()) {
                        Map<String, Object> firstAssess = assessItems.get(0);
                        Map<String, Object> assessData = new LinkedHashMap<>();
                        assessData.put("scoreTotal", firstAssess.get("scoreTotal"));
                        assessData.put("scoreStability", firstAssess.get("scoreStability"));
                        assessData.put("scoreRisk", firstAssess.get("scoreRisk"));
                        assessData.put("riskLevel", firstAssess.get("riskLevel"));
                        assessData.put("qualitativeLabel", firstAssess.get("qualitativeLabel"));
                        assessData.put("industryAvgVol", firstAssess.get("industryAvgVol"));
                        Map<String, Object> donchian = (Map<String, Object>) firstAssess.get("donchian");
                        assessData.put("donchian", donchian != null ? donchian : Map.of());
                        aiNarrativeService.generateAssessmentExplanation(assessData);
                    }

                    List<Map<String, Object>> forecastItems = (List<Map<String, Object>>) forecast.get("items");
                    if (forecastItems != null && !forecastItems.isEmpty()) {
                        Map<String, Object> firstForecast = forecastItems.get(0);
                        Map<String, Object> forecastData = new LinkedHashMap<>();
                        forecastData.put("predictVolatility", firstForecast.get("predictVolatility"));
                        forecastData.put("ciLower", firstForecast.get("ciLower"));
                        forecastData.put("ciUpper", firstForecast.get("ciUpper"));
                        forecastData.put("confidenceLevel", snapshot.getConfidenceLevel());
                        forecastData.put("riskLevel", firstForecast.get("riskLevel"));
                        forecastData.put("modelName", firstForecast.get("modelName"));
                        boolean isBatch = forecastItems.size() > 1;
                        forecastData.put("isBatch", isBatch);
                        forecastData.put("stockCount", forecastItems.size());
                        aiNarrativeService.generateForecastExplanation(forecastData);
                    }
                } catch (Exception e) {
                }
            });
        } catch (Exception ex) {
            updateTask(task, TaskStatus.FAILED, task.getProgress(), ex.getMessage());
            addLog(task.getId(), "FAILED", "FAIL", ex.getMessage(), Map.of("taskNo", task.getTaskNo()));
        }
    }

    private TaskMainEntity buildTask(TaskCreateRequest request) {
        List<String> stockCodes = request.stockCodes() == null ? List.of() : request.stockCodes();
        List<StockBasicEntity> stocks = stockCodes.isEmpty() ? List.of() : stockBasicRepository.findByStockCodeIn(stockCodes);
        TaskMainEntity task = new TaskMainEntity();
        task.setTaskNo(generateTaskNo());
        task.setUserId(authService.currentUser().id());
        task.setTaskType(request.taskType().toUpperCase());
        task.setTaskStatus(TaskStatus.PENDING.name());
        task.setStockMode(request.stockMode().toUpperCase());
        task.setStockCount(Math.max(stockCodes.size(), 1));
        task.setProgress(0);
        task.setStockCode(stockCodes.isEmpty() ? null : stockCodes.get(0));
        task.setStockName(stocks.isEmpty() ? "未知股票" : stocks.get(0).getStockName());
        return task;
    }

    private TaskParamSnapshotEntity buildSnapshot(TaskMainEntity task, TaskCreateRequest request) {
        List<String> chartSpans = normalizeChartSpans(request.chartSpanModes());
        BigDecimal confidence = request.confidenceLevel() == null
                ? appProperties.getDefaultConfidenceLevel()
                : request.confidenceLevel();
        boolean autoRefreshDaily = request.dateEnd().equals(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskType", request.taskType().toUpperCase());
        params.put("dataSourceType", request.dataSourceType().toUpperCase());
        params.put("stockMode", request.stockMode().toUpperCase());
        params.put("stockCodes", request.stockCodes() == null ? List.of() : request.stockCodes());
        params.put("dateStart", request.dateStart().toString());
        params.put("dateEnd", request.dateEnd().toString());
        params.put("chartSpanModes", chartSpans);
        params.put("confidenceLevel", confidence);
        params.put("useDefaultParams", Optional.ofNullable(request.useDefaultParams()).orElse(Boolean.TRUE));
        params.put("timeGranularity", Optional.ofNullable(request.timeGranularity()).orElse("MONTH"));
        params.put("windowSize", Optional.ofNullable(request.windowSize()).orElse(20));
        params.put("forecastType", Optional.ofNullable(request.forecastType()).orElse("MONTH"));
        params.put("autoRefreshDaily", autoRefreshDaily);
        if (request.fileId() != null) {
            params.put("fileId", request.fileId());
            params.put("uploadedFilePath", request.uploadedFilePath());
        }

        TaskParamSnapshotEntity snapshot = new TaskParamSnapshotEntity();
        snapshot.setTaskId(task.getId());
        snapshot.setDataSourceType(request.dataSourceType().toUpperCase());
        snapshot.setStockCodesJson(jsonUtils.toJson(request.stockCodes() == null ? List.of() : request.stockCodes()));
        snapshot.setDateStart(request.dateStart());
        snapshot.setDateEnd(request.dateEnd());
        snapshot.setConfidenceLevel(confidence);
        snapshot.setTimeGranularity(Optional.ofNullable(request.timeGranularity()).orElse("MONTH"));
        snapshot.setForecastHorizon(Optional.ofNullable(request.forecastType()).orElse("MONTH"));
        snapshot.setParamJson(jsonUtils.toJson(params));
        return snapshot;
    }

    private Map<String, Object> buildPythonPayload(TaskMainEntity task, TaskParamSnapshotEntity snapshot) {
        Map<String, Object> params = new LinkedHashMap<>(jsonUtils.toMap(snapshot.getParamJson()));
        params.put("dateStart", snapshot.getDateStart().toString());
        params.put("dateEnd", snapshot.getDateEnd().toString());
        params.put("taskNo", task.getTaskNo());
        params.put("taskId", task.getId());
        params.put("userId", task.getUserId());
        return params;
    }

    private List<String> normalizeChartSpans(List<String> rawModes) {
        List<String> supported = List.of("YEAR", "MONTH", "WEEK");
        if (rawModes == null || rawModes.isEmpty()) {
            return appProperties.getDefaultChartSpans();
        }
        List<String> normalized = rawModes.stream()
                .filter(mode -> mode != null && !mode.isBlank())
                .map(mode -> mode.trim().toUpperCase())
                .filter(mode -> supported.contains(mode) || validCustomSpan(mode))
                .distinct()
                .toList();
        return normalized.isEmpty() ? appProperties.getDefaultChartSpans() : normalized;
    }

    private boolean validCustomSpan(String mode) {
        if (!mode.matches("CUSTOM_\\d{1,3}")) {
            return false;
        }
        int days = Integer.parseInt(mode.substring("CUSTOM_".length()));
        return days >= 2 && days <= 252;
    }

    private String resolveIngestPath(String dataSourceType) {
        return "EXCEL".equalsIgnoreCase(dataSourceType) ? "/py-api/ingest/excel" : "/py-api/ingest/market";
    }

    private void dispatchProcess(String taskNo) {
        eventPublisher.publishEvent(new TaskExecutionRequestedEvent(taskNo));
    }

    private String generateTaskNo() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "T" + timestamp + suffix;
    }

    @SuppressWarnings("unchecked")
    private void syncStocksFromIngest(TaskMainEntity task, Map<String, Object> ingest) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) ingest.getOrDefault("items", List.of());
        if (!items.isEmpty()) {
            Map<String, Object> first = items.get(0);
            task.setStockCode(String.valueOf(first.getOrDefault("stockCode", task.getStockCode())));
            task.setStockName(String.valueOf(first.getOrDefault("stockName", task.getStockName())));
            task.setStockCount(items.size());
            taskMainRepository.save(task);
        }
        for (Map<String, Object> item : items) {
            String stockCode = String.valueOf(item.get("stockCode"));
            StockBasicEntity stock = stockBasicRepository.findByStockCode(stockCode).orElseGet(StockBasicEntity::new);
            stock.setStockCode(stockCode);
            stock.setStockName(String.valueOf(item.getOrDefault("stockName", stockCode)));
            stock.setIndustryName(String.valueOf(item.getOrDefault("industryName", "未分类")));
            stock.setMarketType(String.valueOf(item.getOrDefault("marketType", "CN")));
            stock.setLatestPrice(decimal(item.get("latestPrice")));
            stock.setChangeRate(decimal(item.get("changeRate")));
            stock.setVolume(decimal(item.get("volume")));
            stockBasicRepository.save(stock);
        }
    }

    @SuppressWarnings("unchecked")
    private void persistCalc(TaskMainEntity task, TaskParamSnapshotEntity snapshot, Map<String, Object> response) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getOrDefault("items", List.of());
        Integer windowSize = ((Number) jsonUtils.toMap(snapshot.getParamJson()).getOrDefault("windowSize", 20)).intValue();
        for (Map<String, Object> item : items) {
            String stockCode = String.valueOf(item.get("stockCode"));
            VolatilityCalcResultEntity entity = new VolatilityCalcResultEntity();
            entity.setTaskId(task.getId());
            entity.setStockCode(stockCode);
            entity.setCalcDate(LocalDate.parse(String.valueOf(item.getOrDefault("calcDate", LocalDate.now()))));
            entity.setYzVolatility(decimal(item.get("yzVolatility")));
            entity.setImpliedVolatility(decimal(item.get("impliedVolatility")));
            entity.setWindowSize(windowSize);
            entity.setCalcStatus("SUCCESS");
            entity.setResultJson(jsonUtils.toJson(item));
            calcResultRepository.save(entity);

            stockBasicRepository.findByStockCode(stockCode).ifPresent(stock -> {
                stock.setHistoricalVolatility(entity.getYzVolatility());
                stock.setImpliedVolatility(entity.getImpliedVolatility());
                stockBasicRepository.save(stock);
            });
        }
        addLog(task.getId(), "CALCULATED", "SUCCESS", "波动率计算完成", response);
    }

    @SuppressWarnings("unchecked")
    private void persistAssess(TaskMainEntity task, Map<String, Object> response) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getOrDefault("items", List.of());
        for (Map<String, Object> item : items) {
            String stockCode = String.valueOf(item.get("stockCode"));
            VolatilityAssessResultEntity entity = new VolatilityAssessResultEntity();
            entity.setTaskId(task.getId());
            entity.setStockCode(stockCode);
            entity.setScoreTotal(decimal(item.get("scoreTotal")));
            entity.setScoreStability(decimal(item.get("scoreStability")));
            entity.setScoreRisk(decimal(item.get("scoreRisk")));
            entity.setRiskLevel(String.valueOf(item.getOrDefault("riskLevel", "MEDIUM")));
            entity.setQualitativeLabel(String.valueOf(item.getOrDefault("qualitativeLabel", "中波动")));
            entity.setIndustryAvgVol(decimal(item.get("industryAvgVol")));
            entity.setDonchianJson(jsonUtils.toJson(item.getOrDefault("donchian", Map.of())));
            entity.setResultJson(jsonUtils.toJson(item));
            assessResultRepository.save(entity);

            stockBasicRepository.findByStockCode(stockCode).ifPresent(stock -> {
                stock.setTotalScore(entity.getScoreTotal());
                stock.setRiskLevel(entity.getRiskLevel());
                stockBasicRepository.save(stock);
            });
        }
        addLog(task.getId(), "ASSESSED", "SUCCESS", "综合评估完成", response);
    }

    @SuppressWarnings("unchecked")
    private void persistForecast(TaskMainEntity task, TaskParamSnapshotEntity snapshot, Map<String, Object> response) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getOrDefault("items", List.of());
        for (Map<String, Object> item : items) {
            String stockCode = String.valueOf(item.get("stockCode"));
            VolatilityForecastResultEntity entity = new VolatilityForecastResultEntity();
            entity.setTaskId(task.getId());
            entity.setStockCode(stockCode);
            entity.setForecastType(snapshot.getForecastHorizon());
            entity.setPredictVolatility(decimal(item.get("predictVolatility")));
            entity.setCiLower(decimal(item.get("ciLower")));
            entity.setCiUpper(decimal(item.get("ciUpper")));
            entity.setSerValue(decimal(item.get("serValue")));
            entity.setRiskLevel(String.valueOf(item.getOrDefault("riskLevel", "MEDIUM")));
            entity.setModelName(String.valueOf(item.getOrDefault("modelName", "EWMA")));
            entity.setResultJson(jsonUtils.toJson(item));
            forecastResultRepository.save(entity);

            List<Map<String, Object>> details = (List<Map<String, Object>>) item.getOrDefault("details", List.of());
            for (int index = 0; index < details.size(); index++) {
                Map<String, Object> detail = details.get(index);
                VolatilityForecastDetailEntity detailEntity = new VolatilityForecastDetailEntity();
                detailEntity.setForecastResultId(entity.getId());
                detailEntity.setForecastDate(LocalDate.parse(String.valueOf(detail.get("date"))));
                detailEntity.setPredValue(decimal(detail.get("predValue")));
                detailEntity.setCiLower(decimal(detail.get("ciLower")));
                detailEntity.setCiUpper(decimal(detail.get("ciUpper")));
                detailEntity.setSerValue(decimal(detail.get("serValue")));
                detailEntity.setRankNo(index + 1);
                forecastDetailRepository.save(detailEntity);
            }

            stockBasicRepository.findByStockCode(stockCode).ifPresent(stock -> {
                stock.setPredictedVolatility(entity.getPredictVolatility());
                stock.setRiskLevel(entity.getRiskLevel());
                stockBasicRepository.save(stock);
            });
        }
        addLog(task.getId(), "FORECASTED", "SUCCESS", "预测结果完成", response);
    }

    private void cleanupExistingResults(Long taskId) {
        forecastDetailRepository.deleteByTaskId(taskId);
        forecastResultRepository.deleteByTaskId(taskId);
        assessResultRepository.deleteByTaskId(taskId);
        calcResultRepository.deleteByTaskId(taskId);
    }

    private void refreshRankSnapshots() {
        LocalDate bizDate = LocalDate.now();
        List<StockBasicEntity> stocks = stockBasicRepository.findAll().stream()
                .sorted(Comparator.comparing(StockBasicEntity::getPredictedVolatility,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        for (String scope : List.of("WEEK", "MONTH", "YEAR")) {
            rankSnapshotRepository.deleteBySortScopeAndBizDate(scope, bizDate);
            for (int index = 0; index < stocks.size(); index++) {
                StockBasicEntity stock = stocks.get(index);
                RankSnapshotEntity snapshot = new RankSnapshotEntity();
                snapshot.setBizDate(bizDate);
                snapshot.setSortScope(scope);
                snapshot.setStockCode(stock.getStockCode());
                snapshot.setStockName(stock.getStockName());
                snapshot.setPredVolatility(stock.getPredictedVolatility());
                snapshot.setChangeRate(stock.getChangeRate());
                snapshot.setLastPrice(stock.getLatestPrice());
                snapshot.setRiskLevel(stock.getRiskLevel());
                snapshot.setRankNo(index + 1);
                snapshot.setSnapshotTime(LocalDateTime.now());
                rankSnapshotRepository.save(snapshot);
            }
        }
    }

    private void updateTask(TaskMainEntity task, TaskStatus status, int progress, String summary) {
        task.setTaskStatus(status.name());
        task.setProgress(progress);
        if (task.getStartTime() == null) {
            task.setStartTime(LocalDateTime.now());
        }
        if (status == TaskStatus.FORECASTED || status == TaskStatus.FAILED || status == TaskStatus.AI_DONE) {
            task.setEndTime(LocalDateTime.now());
        }
        if (summary != null) {
            task.setResultSummary(summary);
            if (status == TaskStatus.FAILED) {
                task.setErrorMsg(summary);
            }
        }
        taskMainRepository.save(task);
    }

    private void addLog(Long taskId, String stepCode, String stepStatus, String message, Map<String, Object> detail) {
        TaskStepLogEntity entity = new TaskStepLogEntity();
        entity.setTaskId(taskId);
        entity.setStepCode(stepCode);
        entity.setStepStatus(stepStatus);
        entity.setMessageText(message);
        entity.setDetailJson(jsonUtils.toJson(detail));
        entity.setCreateTime(LocalDateTime.now());
        taskStepLogRepository.save(entity);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(6, RoundingMode.HALF_UP);
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private TaskActionResponse buildActionResponse(TaskMainEntity task) {
        Map<String, Object> errorInfo = findExcelErrorInfo(task.getId());
        Integer errorCount = errorInfo.get("errorCount") instanceof Number number ? number.intValue() : null;
        Boolean errorFileAvailable = errorInfo.get("errorFilePath") != null;
        String downloadUrl = errorFileAvailable ? "/api/data/import/" + task.getTaskNo() + "/errors/download" : null;
        return new TaskActionResponse(task.getTaskNo(), task.getTaskStatus(), errorCount, errorFileAvailable, downloadUrl);
    }

    public Map<String, Object> findExcelErrorInfo(Long taskId) {
        return taskStepLogRepository.findByTaskIdOrderByCreateTimeAsc(taskId).stream()
                .map(log -> jsonUtils.toMap(log.getDetailJson()))
                .filter(detail -> detail.containsKey("errorFilePath"))
                .reduce((first, second) -> second)
                .orElse(Map.of());
    }

    public Path getExcelErrorFilePath(String taskNo) {
        TaskMainEntity task = findTask(taskNo);
        Map<String, Object> info = findExcelErrorInfo(task.getId());
        Object path = info.get("errorFilePath");
        if (path == null) {
            throw new BusinessException(2002, "当前任务没有错误明细文件");
        }
        return Path.of(String.valueOf(path));
    }
}
