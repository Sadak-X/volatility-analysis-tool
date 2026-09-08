package com.volatility.modules.analysis;

import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.AiNarrativeService;
import com.volatility.modules.analysis.entity.VolatilityCalcResultEntity;
import com.volatility.modules.analysis.repository.VolatilityCalcResultRepository;
import com.volatility.modules.stock.StockService;
import com.volatility.modules.task.TaskService;
import com.volatility.modules.task.entity.TaskMainEntity;
import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private final TaskService taskService;
    private final StockService stockService;
    private final VolatilityCalcResultRepository calcResultRepository;
    private final JsonUtils jsonUtils;

    private final AiNarrativeService aiNarrativeService;

    public AnalysisService(
            TaskService taskService,
            StockService stockService,
            VolatilityCalcResultRepository calcResultRepository,
            JsonUtils jsonUtils,
            AiNarrativeService aiNarrativeService) {
        this.taskService = taskService;
        this.stockService = stockService;
        this.calcResultRepository = calcResultRepository;
        this.jsonUtils = jsonUtils;

        this.aiNarrativeService = aiNarrativeService;
    }

    public Map<String, Object> overview(String taskNo) {
        TaskMainEntity task = taskService.findTask(taskNo);
        TaskParamSnapshotEntity snapshot = taskService.findSnapshot(task.getId());
        Optional<VolatilityCalcResultEntity> calcOptional = calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId());

        Map<String, Object> data = buildBaseOverview(task, snapshot);
        if (calcOptional.isEmpty()) {
            data.put("analysisReady", false);
            data.put("historicalVolatility", null);
            data.put("impliedVolatility", null);
            data.put("impliedVolatilityMethod", null);
            data.put("series", List.of());
            data.put("aggregations", Map.of());
            return data;
        }

        VolatilityCalcResultEntity calc = calcOptional.get();
        Map<String, Object> calcMap = jsonUtils.toMap(calc.getResultJson());
        data.put("analysisReady", true);
        data.put("historicalVolatility", calc.getYzVolatility());
        data.put("impliedVolatility", calc.getImpliedVolatility());
        data.put("impliedVolatilityMethod", calcMap.get("impliedVolatilityMethod"));
        data.put("series", firstSelectedSeries(calcMap, snapshot));
        data.put("aggregations", selectedAggregations(calcMap, snapshot));

        Map<String, Object> aiData = new LinkedHashMap<>();
        aiData.put("historicalVolatility", calc.getYzVolatility());
        aiData.put("impliedVolatility", calc.getImpliedVolatility());
        aiData.put("impliedMethod", calcMap.get("impliedVolatilityMethod"));
        data.put("insight", aiNarrativeService.generateAnalysisConclusion(aiData));
        return data;
    }

    public Map<String, Object> trend(String taskNo) {
        TaskMainEntity task = taskService.findTask(taskNo);
        TaskParamSnapshotEntity snapshot = taskService.findSnapshot(task.getId());
        VolatilityCalcResultEntity calc = calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId())
                .orElseThrow(() -> new BusinessException(1002, "分析结果不存在"));
        Map<String, Object> result = jsonUtils.toMap(calc.getResultJson());
        return Map.of(
                "series", firstSelectedSeries(result, snapshot),
                "aggregations", selectedAggregations(result, snapshot)
        );
    }

    @SuppressWarnings("unchecked")
    private Object firstSelectedSeries(Map<String, Object> calcMap, TaskParamSnapshotEntity snapshot) {
        Map<String, Object> aggregations = calcMap.get("aggregations") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw
                : Map.of();

        for (String mode : selectedChartSpans(snapshot)) {
            Object series = aggregations.get(mode);
            if (series instanceof List<?> list && !list.isEmpty()) {
                return series;
            }
        }
        Object trendSeries = calcMap.get("trendSeries");
        return trendSeries == null ? List.of() : trendSeries;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> selectedAggregations(Map<String, Object> calcMap, TaskParamSnapshotEntity snapshot) {
        Map<String, Object> aggregations = calcMap.get("aggregations") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw
                : Map.of();
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String mode : selectedChartSpans(snapshot)) {
            Object series = aggregations.get(mode);
            if (series instanceof List<?> list && !list.isEmpty()) {
                selected.put(mode, series);
            }
        }
        return selected;
    }

    private List<String> selectedChartSpans(TaskParamSnapshotEntity snapshot) {
        List<String> supported = List.of("YEAR", "MONTH", "WEEK");
        Object rawModes = jsonUtils.toMap(snapshot.getParamJson()).get("chartSpanModes");
        if (!(rawModes instanceof List<?> rawList)) {
            return supported;
        }

        List<String> selected = rawList.stream()
                .map(String::valueOf)
                .map(String::toUpperCase)
                .filter(mode -> supported.contains(mode) || mode.matches("CUSTOM_\\d{1,3}"))
                .toList();
        return selected.isEmpty() ? supported : selected;
    }

    private Map<String, Object> buildBaseOverview(TaskMainEntity task, TaskParamSnapshotEntity snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskNo", task.getTaskNo());
        data.put("taskStatus", task.getTaskStatus());
        data.put("progress", task.getProgress());
        data.put("resultSummary", task.getResultSummary());
        data.put("errorMsg", task.getErrorMsg());
        data.put("stock", resolveStock(task));
        data.put("snapshot", jsonUtils.toMap(snapshot.getParamJson()));
        return data;
    }

    private Object resolveStock(TaskMainEntity task) {
        if (task.getStockCode() != null) {
            try {
                return stockService.basic(task.getStockCode());
            } catch (BusinessException ignored) {
            }
        }
        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("stockCode", task.getStockCode());
        stock.put("stockName", task.getStockName());
        return stock;
    }
}
