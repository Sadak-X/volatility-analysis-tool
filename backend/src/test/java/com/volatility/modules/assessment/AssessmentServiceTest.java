package com.volatility.modules.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.AiNarrativeService;
import com.volatility.modules.assessment.entity.VolatilityAssessResultEntity;
import com.volatility.modules.assessment.repository.VolatilityAssessResultRepository;
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
class AssessmentServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private StockService stockService;

    @Mock
    private VolatilityAssessResultRepository assessResultRepository;

    @Mock
    private AiNarrativeService aiNarrativeService;

    private AssessmentService assessmentService;
    private JsonUtils jsonUtils;

    @BeforeEach
    void setUp() {
        jsonUtils = new JsonUtils(new ObjectMapper());
        assessmentService = new AssessmentService(
                taskService,
                stockService,
                assessResultRepository,
                jsonUtils,
                aiNarrativeService
        );
    }

    // TC-ASSS-01
    @Test
    void detailReturnsFullAssessmentWhenResultExists() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot();
        VolatilityAssessResultEntity assess = buildAssessResult();
        StockBasicDto stock = buildStockDto();
        Map<String, Object> insight = buildInsight();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(assessResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L))
                .thenReturn(Optional.of(assess));
        when(stockService.basic("600519")).thenReturn(stock);
        when(aiNarrativeService.generateAssessmentExplanation(anyMap())).thenReturn(insight);

        Map<String, Object> detail = assessmentService.detail("T202604220001");

        assertThat(detail)
                .containsEntry("taskNo", "T202604220001")
                .containsEntry("scoreTotal", new BigDecimal("82.500000"))
                .containsEntry("scoreStability", new BigDecimal("78.000000"))
                .containsEntry("scoreRisk", new BigDecimal("65.000000"))
                .containsEntry("riskLevel", "MEDIUM")
                .containsEntry("qualitativeLabel", "稳健偏中性")
                .containsEntry("industryAvgVol", new BigDecimal("0.320000"))
                .containsEntry("insight", insight);
        assertThat(detail.get("stock")).isEqualTo(stock);
        assertThat(detail.get("snapshot"))
                .isEqualTo(jsonUtils.toMap(snapshot.getParamJson()));
        assertThat(detail.get("donchian"))
                .isEqualTo(jsonUtils.toMap(assess.getDonchianJson()));
        assertThat(detail.get("result"))
                .isEqualTo(jsonUtils.toMap(assess.getResultJson()));
    }

    @Test
    void detailThrowsWhenAssessmentResultIsMissing() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(assessResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentService.detail("T202604220001"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1002)
                .hasMessage("评估结果不存在");
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailPassesScoresAndDonchianToAiInsight() {
        TaskMainEntity task = buildTask();
        TaskParamSnapshotEntity snapshot = buildSnapshot();
        VolatilityAssessResultEntity assess = buildAssessResult();

        when(taskService.findTask("T202604220001")).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(snapshot);
        when(assessResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L))
                .thenReturn(Optional.of(assess));
        when(stockService.basic("600519")).thenReturn(buildStockDto());
        when(aiNarrativeService.generateAssessmentExplanation(anyMap())).thenReturn(buildInsight());

        assessmentService.detail("T202604220001");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiNarrativeService).generateAssessmentExplanation(captor.capture());
        Map<String, Object> aiData = captor.getValue();

        assertThat(aiData)
                .containsEntry("scoreTotal", new BigDecimal("82.500000"))
                .containsEntry("scoreStability", new BigDecimal("78.000000"))
                .containsEntry("scoreRisk", new BigDecimal("65.000000"))
                .containsEntry("riskLevel", "MEDIUM")
                .containsEntry("qualitativeLabel", "稳健偏中性")
                .containsKey("donchian");
        assertThat(aiData.get("donchian"))
                .isEqualTo(jsonUtils.toMap(assess.getDonchianJson()));
    }

    private Map<String, Object> buildInsight() {
        LinkedHashMap<String, Object> insight = new LinkedHashMap<>();
        insight.put("summary", "估值处于中等波动区间");
        insight.put("suggestion", "关注区间突破信号");
        return insight;
    }

    private TaskMainEntity buildTask() {
        TaskMainEntity task = new TaskMainEntity();
        task.setId(1L);
        task.setTaskNo("T202604220001");
        task.setTaskStatus("ASSESSED");
        task.setProgress(100);
        task.setResultSummary("评估结果已生成");
        task.setStockCode("600519");
        task.setStockName("贵州茅台");
        task.setTaskType("ASSESSMENT");
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

    private VolatilityAssessResultEntity buildAssessResult() {
        VolatilityAssessResultEntity entity = new VolatilityAssessResultEntity();
        entity.setTaskId(1L);
        entity.setStockCode("600519");
        entity.setScoreTotal(new BigDecimal("82.500000"));
        entity.setScoreStability(new BigDecimal("78.000000"));
        entity.setScoreRisk(new BigDecimal("65.000000"));
        entity.setRiskLevel("MEDIUM");
        entity.setQualitativeLabel("稳健偏中性");
        entity.setIndustryAvgVol(new BigDecimal("0.320000"));

        LinkedHashMap<String, Object> donchian = new LinkedHashMap<>();
        donchian.put("upper", 0.42);
        donchian.put("lower", 0.18);
        donchian.put("mid", 0.30);
        donchian.put("breakout", "NONE");
        entity.setDonchianJson(jsonUtils.toJson(donchian));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("stabilityScore", 78.0);
        result.put("riskScore", 65.0);
        result.put("windowSize", 20);
        result.put("details", Map.of("annualizedVol", 0.35));
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
                new BigDecimal("82.500000"),
                "MEDIUM",
                LocalDateTime.of(2026, 4, 22, 12, 0)
        );
    }
}