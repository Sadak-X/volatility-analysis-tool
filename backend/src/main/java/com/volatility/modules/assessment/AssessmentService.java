package com.volatility.modules.assessment;

import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.AiNarrativeService;
import com.volatility.modules.assessment.entity.VolatilityAssessResultEntity;
import com.volatility.modules.assessment.repository.VolatilityAssessResultRepository;
import com.volatility.modules.stock.StockService;
import com.volatility.modules.task.TaskService;
import com.volatility.modules.task.entity.TaskMainEntity;
import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AssessmentService {

    private final TaskService taskService;
    private final StockService stockService;
    private final VolatilityAssessResultRepository assessResultRepository;
    private final JsonUtils jsonUtils;
    private final AiNarrativeService aiNarrativeService;


    public AssessmentService(
            TaskService taskService,
            StockService stockService,
            VolatilityAssessResultRepository assessResultRepository,
            JsonUtils jsonUtils,
            AiNarrativeService aiNarrativeService) {
        this.taskService = taskService;
        this.stockService = stockService;
        this.assessResultRepository = assessResultRepository;
        this.jsonUtils = jsonUtils;
        this.aiNarrativeService = aiNarrativeService;
    }

    public Map<String, Object> detail(String taskNo) {
        TaskMainEntity task = taskService.findTask(taskNo);
        TaskParamSnapshotEntity snapshot = taskService.findSnapshot(task.getId());
        VolatilityAssessResultEntity assess = assessResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId())
                .orElseThrow(() -> new BusinessException(1002, "评估结果不存在"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", taskNo);
        result.put("stock", stockService.basic(task.getStockCode()));
        result.put("snapshot", jsonUtils.toMap(snapshot.getParamJson()));
        result.put("scoreTotal", assess.getScoreTotal());
        result.put("scoreStability", assess.getScoreStability());
        result.put("scoreRisk", assess.getScoreRisk());
        result.put("riskLevel", assess.getRiskLevel());
        result.put("qualitativeLabel", assess.getQualitativeLabel());
        result.put("industryAvgVol", assess.getIndustryAvgVol());
        result.put("donchian", jsonUtils.toMap(assess.getDonchianJson()));
        result.put("result", jsonUtils.toMap(assess.getResultJson()));
        Map<String, Object> aiData = new LinkedHashMap<>();
        aiData.put("scoreTotal", assess.getScoreTotal());
        aiData.put("scoreStability", assess.getScoreStability());
        aiData.put("scoreRisk", assess.getScoreRisk());
        aiData.put("riskLevel", assess.getRiskLevel());
        aiData.put("qualitativeLabel", assess.getQualitativeLabel());
        Map<String, Object> donchian = jsonUtils.toMap(assess.getDonchianJson());
        aiData.put("donchian", donchian);
        result.put("insight", aiNarrativeService.generateAssessmentExplanation(aiData));
        return result;
    }
}
