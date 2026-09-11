package com.volatility.modules.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.AiNarrativeService;
import com.volatility.modules.analysis.entity.VolatilityCalcResultEntity;
import com.volatility.modules.analysis.repository.VolatilityCalcResultRepository;
import com.volatility.modules.stock.StockService;
import com.volatility.modules.stock.dto.StockBasicDto;
import com.volatility.modules.task.TaskService;
import com.volatility.modules.task.entity.TaskMainEntity;
import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
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
class AnalysisServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private StockService stockService;

    @Mock
    private VolatilityCalcResultRepository calcResultRepository;

    @Mock
    private AiNarrativeService aiNarrativeService;

    private AnalysisService analysisService;
    private JsonUtils jsonUtils;

    @BeforeEach
    void setUp() {
        jsonUtils = new JsonUtils(new ObjectMapper());
        analysisService = new AnalysisService(
                taskService,
                stockService,
                calcResultRepository,
                jsonUtils,
                aiNarrativeService
        );
    }

    @Test
    void overviewReturnsEmptyAnalysisStateWhenCalcResultIsMissing() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(List.of("YEAR", "MONTH"));
        StockBasicDto stock = buildStockDto();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.empty());
        when(stockService.basic("600519")).thenReturn(stock);

        Map<String, Object> overview = analysisService.overview("T202604220001");

        assertThat(overview)
                .containsEntry("analysisReady", false)
                .containsEntry("historicalVolatility", null)
                .containsEntry("impliedVolatility", null)
                .containsEntry("impliedVolatilityMethod", null);
        assertThat(overview.get("series")).isEqualTo(List.of());
        assertThat(overview.get("aggregations")).isEqualTo(Map.of());
        assertThat(overview.get("stock")).isEqualTo(stock);
    }

    @Test
    void overviewPrefersFirstConfiguredAggregationAndKeepsMethodField() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(List.of("MONTH", "YEAR"));
        LinkedHashMap<String, Object> aggregations = new LinkedHashMap<>();
        aggregations.put("YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41, "impliedValue", 0.29)));
        aggregations.put("MONTH", List.of(Map.of("date", "2026-04-30", "value", 0.35, "impliedValue", 0.26)));
        VolatilityCalcResultEntity calc = buildCalcResult(Map.of(
                "impliedVolatilityMethod", "AKSHARE_OPTION_QVIX",
                "trendSeries", List.of(Map.of("date", "2026-04-20", "value", 0.31)),
                "aggregations", aggregations
        ));
        Map<String, Object> insight = buildInsight();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.of(calc));
        when(stockService.basic("600519")).thenReturn(buildStockDto());
        when(aiNarrativeService.generateAnalysisConclusion(anyMap())).thenReturn(insight);

        Map<String, Object> overview = analysisService.overview("T202604220001");

        assertThat(overview)
                .containsEntry("analysisReady", true)
                .containsEntry("historicalVolatility", new BigDecimal("0.419900"))
                .containsEntry("impliedVolatility", new BigDecimal("0.254900"))
                .containsEntry("impliedVolatilityMethod", "AKSHARE_OPTION_QVIX")
                .containsEntry("insight", insight);
        assertThat(overview.get("series"))
                .isEqualTo(List.of(Map.of("date", "2026-04-30", "value", 0.35, "impliedValue", 0.26)));
        LinkedHashMap<String, Object> expectedAggregations = new LinkedHashMap<>();
        expectedAggregations.put("MONTH", List.of(Map.of("date", "2026-04-30", "value", 0.35, "impliedValue", 0.26)));
        expectedAggregations.put("YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41, "impliedValue", 0.29)));
        assertThat(overview.get("aggregations")).isEqualTo(expectedAggregations);
    }

    @Test
    void overviewFallsBackToTrendSeriesAndTaskMetadataWhenStockLookupFails() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(List.of("WEEK"));
        VolatilityCalcResultEntity calc = buildCalcResult(Map.of(
                "impliedVolatilityMethod", "ROLLING_RETURN_PROXY",
                "trendSeries", List.of(Map.of("date", "2026-04-18", "value", 0.32, "impliedValue", 0.25)),
                "aggregations", Map.of("YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41)))
        ));
        Map<String, Object> insight = buildInsight();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.of(calc));
        when(stockService.basic("600519")).thenThrow(new BusinessException(1002, "股票不存在"));
        when(aiNarrativeService.generateAnalysisConclusion(anyMap())).thenReturn(insight);

        Map<String, Object> overview = analysisService.overview("T202604220001");

        assertThat(overview.get("series"))
                .isEqualTo(List.of(Map.of("date", "2026-04-18", "value", 0.32, "impliedValue", 0.25)));
        assertThat(overview.get("aggregations")).isEqualTo(Map.of());
        assertThat(overview.get("stock")).isEqualTo(Map.of("stockCode", "600519", "stockName", "贵州茅台"));
        assertThat(overview).containsEntry("insight", insight);
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewPassesMetricsToAiInsight() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(List.of("MONTH"));
        VolatilityCalcResultEntity calc = buildCalcResult(Map.of(
                "impliedVolatilityMethod", "AKSHARE_OPTION_QVIX",
                "trendSeries", List.of(Map.of("date", "2026-04-20", "value", 0.31)),
                "aggregations", Map.of(
                        "MONTH", List.of(Map.of("date", "2026-04-30", "value", 0.35, "impliedValue", 0.26))
                )
        ));
        Map<String, Object> insight = buildInsight();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.of(calc));
        when(stockService.basic("600519")).thenReturn(buildStockDto());
        when(aiNarrativeService.generateAnalysisConclusion(anyMap())).thenReturn(insight);

        Map<String, Object> overview = analysisService.overview("T202604220001");

        assertThat(overview).containsEntry("insight", insight);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiNarrativeService).generateAnalysisConclusion(captor.capture());
        Map<String, Object> aiData = captor.getValue();
        assertThat(aiData)
                .containsEntry("historicalVolatility", new BigDecimal("0.419900"))
                .containsEntry("impliedVolatility", new BigDecimal("0.254900"))
                .containsEntry("impliedMethod", "AKSHARE_OPTION_QVIX");
    }

    // TC-ANAL-03
    @Test
    void trendReturnsSeriesAndAggregationsWhenResultExists() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(List.of("MONTH", "YEAR"));
        LinkedHashMap<String, Object> aggregations = new LinkedHashMap<>();
        aggregations.put("YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41, "impliedValue", 0.29)));
        aggregations.put("MONTH", List.of(Map.of("date", "2026-04-30", "value", 0.35, "impliedValue", 0.26)));
        VolatilityCalcResultEntity calc = buildCalcResult(Map.of(
                "impliedVolatilityMethod", "AKSHARE_OPTION_QVIX",
                "trendSeries", List.of(Map.of("date", "2026-04-20", "value", 0.31)),
                "aggregations", aggregations
        ));

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.of(calc));

        Map<String, Object> trend = analysisService.trend("T202604220001");

        assertThat(trend.get("series"))
                .isEqualTo(List.of(Map.of("date", "2026-04-30", "value", 0.35, "impliedValue", 0.26)));
        LinkedHashMap<String, Object> expectedAggregations = new LinkedHashMap<>();
        expectedAggregations.put("MONTH", List.of(Map.of("date", "2026-04-30", "value", 0.35, "impliedValue", 0.26)));
        expectedAggregations.put("YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41, "impliedValue", 0.29)));
        assertThat(trend.get("aggregations")).isEqualTo(expectedAggregations);
    }

    @Test
    void trendFallsBackToTrendSeriesWhenConfiguredSpansHaveNoData() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(List.of("WEEK", "CUSTOM_5"));
        VolatilityCalcResultEntity calc = buildCalcResult(Map.of(
                "impliedVolatilityMethod", "ROLLING_RETURN_PROXY",
                "trendSeries", List.of(Map.of("date", "2026-04-18", "value", 0.32)),
                "aggregations", Map.of(
                        "YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41))
                )
        ));

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.of(calc));

        Map<String, Object> trend = analysisService.trend("T202604220001");

        assertThat(trend.get("series"))
                .isEqualTo(List.of(Map.of("date", "2026-04-18", "value", 0.32)));
        assertThat(trend.get("aggregations")).isEqualTo(Map.of());
    }

    @Test
    void trendThrowsWhenCalcResultIsMissing() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(List.of("MONTH"));

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.trend("T202604220001"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1002)
                .hasMessage("分析结果不存在");
    }

    @Test
    void selectedChartSpansDefaultToSupportedModesWhenConfigIsMissing() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot(null);
        LinkedHashMap<String, Object> aggregations = new LinkedHashMap<>();
        aggregations.put("YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41)));
        aggregations.put("MONTH", List.of(Map.of("date", "2026-04-30", "value", 0.35)));
        aggregations.put("WEEK", List.of(Map.of("date", "2026-04-25", "value", 0.33)));
        VolatilityCalcResultEntity calc = buildCalcResult(Map.of(
                "impliedVolatilityMethod", "AKSHARE_OPTION_QVIX",
                "trendSeries", List.of(Map.of("date", "2026-04-20", "value", 0.31)),
                "aggregations", aggregations
        ));

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.of(calc));

        Map<String, Object> trend = analysisService.trend("T202604220001");

        assertThat(trend.get("series"))
                .isEqualTo(List.of(Map.of("date", "2026-12-31", "value", 0.41)));
        LinkedHashMap<String, Object> expectedAggregations = new LinkedHashMap<>();
        expectedAggregations.put("YEAR", List.of(Map.of("date", "2026-12-31", "value", 0.41)));
        expectedAggregations.put("MONTH", List.of(Map.of("date", "2026-04-30", "value", 0.35)));
        expectedAggregations.put("WEEK", List.of(Map.of("date", "2026-04-25", "value", 0.33)));
        assertThat(trend.get("aggregations")).isEqualTo(expectedAggregations);
    }

    private Map<String, Object> buildInsight() {
        LinkedHashMap<String, Object> insight = new LinkedHashMap<>();
        insight.put("summary", "近期波动率处于中等水平");
        insight.put("suggestion", "关注隐含波动率变化");
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
        task.setTaskType("ANALYSIS");
        task.setStockMode("SINGLE");
        task.setStockCount(1);
        task.setUserId(10L);
        task.setCreateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        task.setUpdateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        return task;
    }

    private TaskParamSnapshotEntity buildSnapshot(List<String> chartSpanModes) {
        TaskParamSnapshotEntity snapshot = new TaskParamSnapshotEntity();
        snapshot.setTaskId(1L);
        snapshot.setDataSourceType("AKSHARE");
        snapshot.setStockCodesJson("[\"600519\"]");
        snapshot.setDateStart(LocalDate.of(2025, 1, 1));
        snapshot.setDateEnd(LocalDate.of(2026, 4, 22));
        snapshot.setConfidenceLevel(new BigDecimal("0.90"));
        snapshot.setTimeGranularity("MONTH");
        snapshot.setForecastHorizon("MONTH");
        LinkedHashMap<String, Object> param = new LinkedHashMap<>();
        if (chartSpanModes != null) {
            param.put("chartSpanModes", chartSpanModes);
        }
        param.put("confidenceLevel", 0.90);
        snapshot.setParamJson(jsonUtils.toJson(param));
        snapshot.setCreateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        snapshot.setUpdateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        return snapshot;
    }

    private VolatilityCalcResultEntity buildCalcResult(Map<String, Object> result) {
        VolatilityCalcResultEntity entity = new VolatilityCalcResultEntity();
        entity.setTaskId(1L);
        entity.setStockCode("600519");
        entity.setCalcDate(LocalDate.of(2026, 4, 22));
        entity.setYzVolatility(new BigDecimal("0.419900"));
        entity.setImpliedVolatility(new BigDecimal("0.254900"));
        entity.setWindowSize(20);
        entity.setCalcStatus("SUCCESS");
        entity.setResultJson(jsonUtils.toJson(result));
        entity.setCreateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        entity.setUpdateTime(LocalDateTime.of(2026, 4, 22, 12, 0));
        return entity;
    }

    private StockBasicDto buildStockDto() {
        return new StockBasicDto(
                "600519",
                "贵州茅台",
                "白酒",
                "CN",
                new BigDecimal("205.360000"),
                new BigDecimal("0.010000"),
                new BigDecimal("10000.000000"),
                new BigDecimal("0.419900"),
                new BigDecimal("0.254900"),
                new BigDecimal("0.300000"),
                new BigDecimal("75.000000"),
                "MEDIUM",
                LocalDateTime.of(2026, 4, 22, 12, 0)
        );
    }
}