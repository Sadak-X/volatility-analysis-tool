package com.volatility.modules.forecast;

import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.AiNarrativeService;
import com.volatility.modules.forecast.entity.VolatilityForecastResultEntity;
import com.volatility.modules.forecast.repository.VolatilityForecastDetailRepository;
import com.volatility.modules.forecast.repository.VolatilityForecastResultRepository;
import com.volatility.modules.stock.StockService;
import com.volatility.modules.stock.dto.StockBasicDto;
import com.volatility.modules.task.TaskService;
import com.volatility.modules.task.entity.TaskMainEntity;
import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
import com.volatility.modules.task.repository.TaskMainRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ForecastService {

    private final VolatilityForecastResultRepository forecastResultRepository;
    private final VolatilityForecastDetailRepository forecastDetailRepository;
    private final TaskService taskService;
    private final StockService stockService;
    private final JsonUtils jsonUtils;
    private final TaskMainRepository taskMainRepository;

    private final AiNarrativeService aiNarrativeService;

    public ForecastService(
            VolatilityForecastResultRepository forecastResultRepository,
            VolatilityForecastDetailRepository forecastDetailRepository,
            TaskService taskService,
            StockService stockService,
            JsonUtils jsonUtils,
            TaskMainRepository taskMainRepository,
            AiNarrativeService aiNarrativeService) {
        this.forecastResultRepository = forecastResultRepository;
        this.forecastDetailRepository = forecastDetailRepository;
        this.taskService = taskService;
        this.stockService = stockService;
        this.jsonUtils = jsonUtils;
        this.taskMainRepository = taskMainRepository;
        this.aiNarrativeService = aiNarrativeService;

    }

    public Map<String, Object> page(String stockCode, int pageNo, int pageSize) {
        String stockCodeFilter = stockCode == null ? "" : stockCode;
        Page<VolatilityForecastResultEntity> page = forecastResultRepository.findByStockCodeContaining(
                stockCodeFilter,
                PageRequest.of(
                        Math.max(pageNo - 1, 0),
                        pageSize,
                        Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"))));
        Map<String, Object> riskCounts = Map.of(
                "HIGH", forecastResultRepository.countByStockCodeContainingAndRiskLevel(stockCodeFilter, "HIGH"),
                "MEDIUM", forecastResultRepository.countByStockCodeContainingAndRiskLevel(stockCodeFilter, "MEDIUM"),
                "LOW", forecastResultRepository.countByStockCodeContainingAndRiskLevel(stockCodeFilter, "LOW")
        );
        return Map.of(
                "records", page.getContent().stream().map(item -> {
                    Map<String, Object> record = new LinkedHashMap<>();
                    String taskNo = taskMainRepository.findById(item.getTaskId()).map(TaskMainEntity::getTaskNo).orElse("");
                    record.put("taskId", item.getTaskId());
                    record.put("taskNo", taskNo);
                    record.put("stockCode", item.getStockCode());
                    record.put("forecastType", item.getForecastType());
                    record.put("predictVolatility", item.getPredictVolatility());
                    record.put("ciLower", item.getCiLower());
                    record.put("ciUpper", item.getCiUpper());
                    record.put("serValue", item.getSerValue());
                    record.put("riskLevel", item.getRiskLevel());
                    record.put("modelName", item.getModelName());
                    record.put("createTime", item.getCreateTime());
                    return record;
                }).toList(),
                "total", page.getTotalElements(),
                "riskCounts", riskCounts,
                "pageNo", pageNo,
                "pageSize", pageSize
        );
    }

    public Map<String, Object> detail(String taskNo) {
        TaskMainEntity task = taskService.findTask(taskNo);
        TaskParamSnapshotEntity snapshot = taskService.findSnapshot(task.getId());
        List<VolatilityForecastResultEntity> forecasts = forecastResultRepository.findByTaskIdOrderByIdAsc(task.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskNo", taskNo);
        data.put("taskStatus", task.getTaskStatus());
        data.put("progress", task.getProgress());
        data.put("resultSummary", task.getResultSummary());
        data.put("errorMsg", task.getErrorMsg());
        data.put("stockCount", task.getStockCount());
        data.put("confidenceLevel", snapshot.getConfidenceLevel());
        data.put("chartSpanModes", jsonUtils.toMap(snapshot.getParamJson()).get("chartSpanModes"));
        data.put("forecastReady", !forecasts.isEmpty());
        if (forecasts.isEmpty()) {
            return data;
        }

        List<Map<String, Object>> results = forecasts.stream().map(forecast -> {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> stock = stock(forecast.getStockCode());
            List<Map<String, Object>> details = detailItems(forecast);
            result.put("stock", stock);
            result.put("stockCode", forecast.getStockCode());
            result.put("stockName", stock.getOrDefault("stockName", forecast.getStockCode()));
            result.put("forecastType", forecast.getForecastType());
            result.put("predictVolatility", forecast.getPredictVolatility());
            result.put("ciLower", forecast.getCiLower());
            result.put("ciUpper", forecast.getCiUpper());
            result.put("serValue", forecast.getSerValue());
            result.put("riskLevel", forecast.getRiskLevel());
            result.put("modelName", forecast.getModelName());
            result.put("series", details);
            result.put("seriesByType", forecastSeriesByType(forecast, details));
            return result;
        }).toList();

        VolatilityForecastResultEntity first = forecasts.get(0);
        Map<String, Object> firstResult = results.get(0);
        data.put("stock", stock(first.getStockCode()));
        data.put("isBatch", forecasts.size() > 1);
        data.put("results", results);
        data.put("comparisonSeries", results.stream().map(result -> {
            Map<String, Object> series = new LinkedHashMap<>();
            series.put("name", result.get("stockName") + " / " + result.get("stockCode"));
            series.put("stockCode", result.get("stockCode"));
            series.put("data", result.get("series"));
            return series;
        }).toList());
        data.put("comparisonSeriesByType", comparisonSeriesByType(results));
        data.put("predictVolatility", average(forecasts.stream().map(VolatilityForecastResultEntity::getPredictVolatility).toList()));
        data.put("ciLower", forecasts.stream()
                .map(VolatilityForecastResultEntity::getCiLower)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO));
        data.put("ciUpper", forecasts.stream()
                .map(VolatilityForecastResultEntity::getCiUpper)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO));
        data.put("serValue", average(forecasts.stream().map(VolatilityForecastResultEntity::getSerValue).toList()));
        data.put("riskLevel", highestRisk(forecasts.stream().map(VolatilityForecastResultEntity::getRiskLevel).toList()));
        data.put("modelName", forecasts.size() > 1 ? "多股票对比" : first.getModelName());
        data.put("series", firstResult.get("series"));
        data.put("seriesByType", firstResult.get("seriesByType"));
        Map<String, Object> aiData = new LinkedHashMap<>();
        aiData.put("predictVolatility", data.get("predictVolatility"));
        aiData.put("ciLower", data.get("ciLower"));
        aiData.put("ciUpper", data.get("ciUpper"));
        aiData.put("confidenceLevel", snapshot.getConfidenceLevel());
        aiData.put("riskLevel", data.get("riskLevel"));
        aiData.put("modelName", data.get("modelName"));
        aiData.put("isBatch", data.get("isBatch"));
        aiData.put("stockCount", task.getStockCount());
        data.put("insight", aiNarrativeService.generateForecastExplanation(aiData));
        return data;
    }

    public List<Map<String, Object>> items(String taskNo) {
        TaskMainEntity task = taskService.findTask(taskNo);
        List<VolatilityForecastResultEntity> forecasts = forecastResultRepository.findByTaskIdOrderByIdAsc(task.getId());
        if (forecasts.isEmpty()) {
            throw new BusinessException(1002, "预测结果不存在");
        }
        return forecasts.stream().flatMap(forecast -> detailItems(forecast).stream()).toList();
    }

    private List<Map<String, Object>> detailItems(VolatilityForecastResultEntity forecast) {
        return forecastDetailRepository.findByForecastResultIdOrderByRankNoAsc(forecast.getId()).stream().map(item -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("stockCode", forecast.getStockCode());
            detail.put("stockName", stock(forecast.getStockCode()).getOrDefault("stockName", forecast.getStockCode()));
            detail.put("date", item.getForecastDate());
            detail.put("predValue", item.getPredValue());
            detail.put("ciLower", item.getCiLower());
            detail.put("ciUpper", item.getCiUpper());
            detail.put("serValue", item.getSerValue());
            detail.put("rankNo", item.getRankNo());
            return detail;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> forecastSeriesByType(VolatilityForecastResultEntity forecast, List<Map<String, Object>> fallback) {
        Map<String, Object> resultJson = jsonUtils.toMap(forecast.getResultJson());
        Object rawSpanSeries = resultJson.get("spanSeries");
        if (rawSpanSeries instanceof Map<?, ?> rawMap) {
            Map<String, Object> seriesByType = new LinkedHashMap<>();
            for (String type : List.of("DAY", "MONTH", "YEAR")) {
                Object series = rawMap.get(type);
                seriesByType.put(type, series instanceof List<?> ? series : List.of());
            }
            return seriesByType;
        }

        Map<String, Object> seriesByType = new LinkedHashMap<>();
        seriesByType.put(forecast.getForecastType(), fallback);
        return seriesByType;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> comparisonSeriesByType(List<Map<String, Object>> results) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String type : List.of("DAY", "MONTH", "YEAR")) {
            List<Map<String, Object>> series = results.stream().map(result -> {
                Map<String, Object> item = new LinkedHashMap<>();
                Map<String, Object> seriesByType = result.get("seriesByType") instanceof Map<?, ?> raw
                        ? (Map<String, Object>) raw
                        : Map.of();
                item.put("name", result.get("stockName") + " / " + result.get("stockCode"));
                item.put("stockCode", result.get("stockCode"));
                item.put("data", seriesByType.getOrDefault(type, List.of()));
                return item;
            }).toList();
            data.put(type, series);
        }
        return data;
    }

    private Map<String, Object> stock(String stockCode) {
        try {
            StockBasicDto basic = stockService.basic(stockCode);
            Map<String, Object> stock = new LinkedHashMap<>();
            stock.put("stockCode", basic.stockCode());
            stock.put("stockName", basic.stockName());
            stock.put("industryName", basic.industryName());
            stock.put("marketType", basic.marketType());
            stock.put("latestPrice", basic.latestPrice());
            stock.put("changeRate", basic.changeRate());
            stock.put("volume", basic.volume());
            stock.put("historicalVolatility", basic.historicalVolatility());
            stock.put("impliedVolatility", basic.impliedVolatility());
            stock.put("predictedVolatility", basic.predictedVolatility());
            stock.put("totalScore", basic.totalScore());
            stock.put("riskLevel", basic.riskLevel());
            stock.put("updateTime", basic.updateTime());
            return stock;
        } catch (Exception ex) {
            return Map.of("stockCode", stockCode, "stockName", stockCode);
        }
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> available = values.stream().filter(value -> value != null).toList();
        if (available.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = available.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(available.size()), 6, RoundingMode.HALF_UP);
    }

    private String highestRisk(List<String> riskLevels) {
        if (riskLevels.contains("HIGH")) {
            return "HIGH";
        }
        if (riskLevels.contains("MEDIUM")) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
