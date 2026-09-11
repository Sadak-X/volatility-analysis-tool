package com.volatility.modules.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.AiNarrativeService;
import com.volatility.modules.forecast.entity.VolatilityForecastDetailEntity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private VolatilityForecastResultRepository forecastResultRepository;

    @Mock
    private VolatilityForecastDetailRepository forecastDetailRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private StockService stockService;

    @Mock
    private TaskMainRepository taskMainRepository;

    @Mock
    private AiNarrativeService aiNarrativeService;

    private ForecastService forecastService;
    private JsonUtils jsonUtils;

    @BeforeEach
    void setUp() {
        jsonUtils = new JsonUtils(new ObjectMapper());
        forecastService = new ForecastService(
                forecastResultRepository,
                forecastDetailRepository,
                taskService,
                stockService,
                jsonUtils,
                taskMainRepository,
                aiNarrativeService
        );
    }

    // TC-FORC-03
    @Test
    void detailReturnsSingleStockForecastWhenOnlyOneResult() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot();
        VolatilityForecastResultEntity forecast = buildForecastResult("600519", "GARCH", "MEDIUM");
        List<VolatilityForecastDetailEntity> details = buildDetailItems(forecast.getId());
        Map<String, Object> insight = buildInsight();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(forecastResultRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(forecast));
        when(forecastDetailRepository.findByForecastResultIdOrderByRankNoAsc(forecast.getId()))
                .thenReturn(details);
        when(stockService.basic("600519")).thenReturn(buildStockDto("600519", "贵州茅台"));
        when(aiNarrativeService.generateForecastExplanation(anyMap())).thenReturn(insight);

        Map<String, Object> data = forecastService.detail("T202604220001");

        assertThat(data)
                .containsEntry("taskNo", "T202604220001")
                .containsEntry("taskStatus", "FORECASTED")
                .containsEntry("progress", 100)
                .containsEntry("isBatch", false)
                .containsEntry("stockCount", 1)
                .containsEntry("riskLevel", "MEDIUM")
                .containsEntry("modelName", "GARCH")
                .containsEntry("insight", insight);
        assertThat(data.get("forecastReady")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0))
                .containsEntry("stockCode", "600519")
                .containsEntry("stockName", "贵州茅台")
                .containsEntry("modelName", "GARCH")
                .containsEntry("riskLevel", "MEDIUM");
        assertThat(data.get("series")).isNotNull();
    }

    @Test
    void detailAggregatesMultipleStocksAsBatch() {
        TaskMainEntity task = buildTask();
        task.setStockCount(2);
        TaskParamSnapshotEntity snapshot = buildSnapshot();
        VolatilityForecastResultEntity first = buildForecastResult("600519", "GARCH", "MEDIUM");
        VolatilityForecastResultEntity second = buildForecastResult("000001", "GARCH", "HIGH");
        Map<String, Object> insight = buildInsight();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(forecastResultRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));
        when(forecastDetailRepository.findByForecastResultIdOrderByRankNoAsc(first.getId()))
                .thenReturn(buildDetailItems(first.getId()));
        when(forecastDetailRepository.findByForecastResultIdOrderByRankNoAsc(second.getId()))
                .thenReturn(buildDetailItems(second.getId()));
        when(stockService.basic("600519")).thenReturn(buildStockDto("600519", "贵州茅台"));
        when(stockService.basic("000001")).thenReturn(buildStockDto("000001", "平安银行"));
        when(aiNarrativeService.generateForecastExplanation(anyMap())).thenReturn(insight);

        Map<String, Object> data = forecastService.detail("T202604220001");

        assertThat(data)
                .containsEntry("isBatch", true)
                .containsEntry("stockCount", 2)
                .containsEntry("modelName", "多股票对比")
                .containsEntry("riskLevel", "HIGH")
                .containsEntry("insight", insight);
        assertThat(data.get("predictVolatility")).isEqualTo(new BigDecimal("0.300000"));
        assertThat(data.get("ciLower")).isEqualTo(new BigDecimal("0.200000"));
        assertThat(data.get("ciUpper")).isEqualTo(new BigDecimal("0.450000"));
    }

    @Test
    void detailReturnsEmptyStateWhenNoForecastExists() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(forecastResultRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of());

        Map<String, Object> data = forecastService.detail("T202604220001");

        assertThat(data)
                .containsEntry("forecastReady", false)
                .containsEntry("taskNo", "T202604220001")
                .containsEntry("taskStatus", "FORECASTED");
        assertThat(data).doesNotContainKey("results");
        assertThat(data).doesNotContainKey("insight");
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailPassesAggregatedMetricsToAiInsight() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot();
        VolatilityForecastResultEntity forecast = buildForecastResult("600519", "GARCH", "MEDIUM");
        Map<String, Object> insight = buildInsight();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(forecastResultRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(forecast));
        when(forecastDetailRepository.findByForecastResultIdOrderByRankNoAsc(forecast.getId()))
                .thenReturn(buildDetailItems(forecast.getId()));
        when(stockService.basic("600519")).thenReturn(buildStockDto("600519", "贵州茅台"));
        when(aiNarrativeService.generateForecastExplanation(anyMap())).thenReturn(insight);

        forecastService.detail("T202604220001");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiNarrativeService).generateForecastExplanation(captor.capture());
        Map<String, Object> aiData = captor.getValue();

        assertThat(aiData)
                .containsEntry("predictVolatility", new BigDecimal("0.300000"))
                .containsEntry("ciLower", new BigDecimal("0.200000"))
                .containsEntry("ciUpper", new BigDecimal("0.450000"))
                .containsEntry("confidenceLevel", new BigDecimal("0.90"))
                .containsEntry("riskLevel", "MEDIUM")
                .containsEntry("modelName", "GARCH")
                .containsEntry("isBatch", false)
                .containsEntry("stockCount", 1);
    }

    @Test
    void itemsReturnsFlattenedDetailItems() {
        TaskMainEntity task = buildTask();
        VolatilityForecastResultEntity forecast = buildForecastResult("600519", "GARCH", "MEDIUM");

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(forecastResultRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(forecast));
        when(forecastDetailRepository.findByForecastResultIdOrderByRankNoAsc(forecast.getId()))
                .thenReturn(buildDetailItems(forecast.getId()));
        when(stockService.basic("600519")).thenReturn(buildStockDto("600519", "贵州茅台"));

        List<Map<String, Object>> items = forecastService.items("T202604220001");

        assertThat(items).hasSize(2);
        assertThat(items.get(0))
                .containsEntry("stockCode", "600519")
                .containsEntry("stockName", "贵州茅台")
                .containsEntry("date", LocalDate.of(2026, 5, 1))
                .containsEntry("rankNo", 1);
        assertThat(items.get(1))
                .containsEntry("date", LocalDate.of(2026, 6, 1))
                .containsEntry("rankNo", 2);
    }

    @Test
    void itemsThrowsWhenForecastResultIsMissing() {
        TaskMainEntity task = buildTask();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(forecastResultRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> forecastService.items("T202604220001"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1002)
                .hasMessage("预测结果不存在");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageReturnsRecordsWithRiskCounts() {
        VolatilityForecastResultEntity entity = buildForecastResult("600519", "GARCH", "MEDIUM");
        TaskMainEntity task = buildTask();
        org.springframework.data.domain.Page<VolatilityForecastResultEntity> page =
                new org.springframework.data.domain.PageImpl<>(List.of(entity));

        when(forecastResultRepository.findByStockCodeContaining(eq("600519"), any()))
                .thenReturn(page);
        when(forecastResultRepository.countByStockCodeContainingAndRiskLevel("600519", "HIGH")).thenReturn(0L);
        when(forecastResultRepository.countByStockCodeContainingAndRiskLevel("600519", "MEDIUM")).thenReturn(1L);
        when(forecastResultRepository.countByStockCodeContainingAndRiskLevel("600519", "LOW")).thenReturn(0L);
        when(taskMainRepository.findById(entity.getTaskId())).thenReturn(Optional.of(task));

        Map<String, Object> data = forecastService.page("600519", 1, 10);

        assertThat(data)
                .containsEntry("total", 1L)
                .containsEntry("pageNo", 1)
                .containsEntry("pageSize", 10);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0))
                .containsEntry("taskNo", "T202604220001")
                .containsEntry("stockCode", "600519")
                .containsEntry("modelName", "GARCH")
                .containsEntry("riskLevel", "MEDIUM");
        Map<String, Object> riskCounts = (Map<String, Object>) data.get("riskCounts");
        assertThat(riskCounts)
                .containsEntry("HIGH", 0L)
                .containsEntry("MEDIUM", 1L)
                .containsEntry("LOW", 0L);
    }

    private Map<String, Object> buildInsight() {
        LinkedHashMap<String, Object> insight = new LinkedHashMap<>();
        insight.put("summary", "短期波动率预计维持中位");
        insight.put("suggestion", "保持仓位观察");
        return insight;
    }

    private TaskMainEntity buildTask() {
        TaskMainEntity task = new TaskMainEntity();
        task.setId(1L);
        task.setTaskNo("T202604220001");
        task.setTaskStatus("FORECASTED");
        task.setProgress(100);
        task.setResultSummary("预测结果已生成");
        task.setStockCode("600519");
        task.setStockName("贵州茅台");
        task.setTaskType("FORECAST");
        task.setStockMode("SINGLE");
        task.setStockCount(1);
        task.setUserId(10L);
        task.setCreateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        task.setUpdateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        return task;
    }

    private TaskParamSnapshotEntity buildSnapshot() {
        TaskParamSnapshotEntity snapshot = new TaskParamSnapshotEntity();
        snapshot.setTaskId(1L);
        snapshot.setDataSourceType("AKSHARE");
        snapshot.setStockCodesJson("[\"600519\"]");
        snapshot.setDateStart(LocalDate.of(2025, 1, 1));
        snapshot.setDateEnd(LocalDate.of(2026, 4, 22));
        snapshot.setConfidenceLevel(new BigDecimal("0.90"));
        snapshot.setTimeGranularity("MONTH");
        snapshot.setForecastHorizon("MONTH");
        snapshot.setParamJson(jsonUtils.toJson(Map.of(
                "chartSpanModes", List.of("YEAR", "MONTH"),
                "confidenceLevel", 0.90
        )));
        snapshot.setCreateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        snapshot.setUpdateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        return snapshot;
    }

    private VolatilityForecastResultEntity buildForecastResult(String stockCode, String modelName, String riskLevel) {
        VolatilityForecastResultEntity entity = new VolatilityForecastResultEntity();
        entity.setId((long) Math.abs(stockCode.hashCode() % 1000000) + 1);
        entity.setTaskId(1L);
        entity.setStockCode(stockCode);
        entity.setForecastType("MONTH");
        entity.setPredictVolatility(new BigDecimal("0.300000"));
        entity.setCiLower(new BigDecimal("0.200000"));
        entity.setCiUpper(new BigDecimal("0.450000"));
        entity.setSerValue(new BigDecimal("0.050000"));
        entity.setRiskLevel(riskLevel);
        entity.setModelName(modelName);

        LinkedHashMap<String, Object> resultJson = new LinkedHashMap<>();
        LinkedHashMap<String, Object> spanSeries = new LinkedHashMap<>();
        spanSeries.put("DAY", List.of(Map.of("date", "2026-04-22", "predValue", 0.30)));
        spanSeries.put("MONTH", List.of(Map.of("date", "2026-04-30", "predValue", 0.30)));
        spanSeries.put("YEAR", List.of(Map.of("date", "2026-12-31", "predValue", 0.30)));
        resultJson.put("spanSeries", spanSeries);
        entity.setResultJson(jsonUtils.toJson(resultJson));

        entity.setCreateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        entity.setUpdateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        return entity;
    }

    private List<VolatilityForecastDetailEntity> buildDetailItems(Long forecastResultId) {
        VolatilityForecastDetailEntity first = new VolatilityForecastDetailEntity();
        first.setId(1L);
        first.setForecastResultId(forecastResultId);
        first.setForecastDate(LocalDate.of(2026, 5, 1));
        first.setPredValue(new BigDecimal("0.310000"));
        first.setCiLower(new BigDecimal("0.210000"));
        first.setCiUpper(new BigDecimal("0.420000"));
        first.setSerValue(new BigDecimal("0.050000"));
        first.setRankNo(1);

        VolatilityForecastDetailEntity second = new VolatilityForecastDetailEntity();
        second.setId(2L);
        second.setForecastResultId(forecastResultId);
        second.setForecastDate(LocalDate.of(2026, 6, 1));
        second.setPredValue(new BigDecimal("0.320000"));
        second.setCiLower(new BigDecimal("0.220000"));
        second.setCiUpper(new BigDecimal("0.430000"));
        second.setSerValue(new BigDecimal("0.050000"));
        second.setRankNo(2);

        return List.of(first, second);
    }

    private StockBasicDto buildStockDto(String stockCode, String stockName) {
        return new StockBasicDto(
                stockCode,
                stockName,
                "银行",
                "CN",
                new BigDecimal("10.500000"),
                new BigDecimal("0.010000"),
                new BigDecimal("100000.000000"),
                new BigDecimal("0.300000"),
                new BigDecimal("0.250000"),
                new BigDecimal("0.300000"),
                new BigDecimal("82.500000"),
                "MEDIUM",
                LocalDateTime.of(2026, 4, 22, 12, 0)
        );
    }
}