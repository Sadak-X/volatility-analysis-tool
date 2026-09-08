package com.volatility.modules.ai;

import com.volatility.common.config.AppProperties;
import com.volatility.common.enums.TaskStatus;
import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.dto.AiFollowupRequest;
import com.volatility.modules.ai.dto.AiGenerateRequest;
import com.volatility.modules.ai.entity.AiAnalysisReportEntity;
import com.volatility.modules.ai.entity.AiConclusionEntity;
import com.volatility.modules.ai.repository.AiAnalysisReportRepository;
import com.volatility.modules.ai.repository.AiConclusionRepository;
import com.volatility.modules.analysis.entity.VolatilityCalcResultEntity;
import com.volatility.modules.analysis.repository.VolatilityCalcResultRepository;
import com.volatility.modules.assessment.entity.VolatilityAssessResultEntity;
import com.volatility.modules.assessment.repository.VolatilityAssessResultRepository;
import com.volatility.modules.forecast.entity.VolatilityForecastResultEntity;
import com.volatility.modules.forecast.repository.VolatilityForecastResultRepository;
import com.volatility.modules.task.PythonClient;
import com.volatility.modules.task.TaskService;
import com.volatility.modules.task.entity.TaskMainEntity;
import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
import com.volatility.modules.task.repository.TaskMainRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Service
public class AiService {

    private static final String RISK_DISCLAIMER = "投资有风险，分析结果仅供研究参考，不构成投资建议。";

    private final TaskService taskService;
    private final TaskMainRepository taskMainRepository;
    private final AiAnalysisReportRepository reportRepository;
    private final PythonClient pythonClient;
    private final JsonUtils jsonUtils;
    private final RestTemplate restTemplate;
    private final AppProperties appProperties;
    private final AiNarrativeService aiNarrativeService;
    private final VolatilityCalcResultRepository calcResultRepository;
    private final VolatilityAssessResultRepository assessResultRepository;
    private final VolatilityForecastResultRepository forecastResultRepository;

    private final AiConclusionRepository conclusionRepository;
    public AiService(
            TaskService taskService,
            TaskMainRepository taskMainRepository,
            AiAnalysisReportRepository reportRepository,
            PythonClient pythonClient,
            JsonUtils jsonUtils,
            @Qualifier("deepSeekRestTemplate") RestTemplate restTemplate,
            AppProperties appProperties,
            AiNarrativeService aiNarrativeService,
            VolatilityCalcResultRepository calcResultRepository,
            VolatilityAssessResultRepository assessResultRepository,
            VolatilityForecastResultRepository forecastResultRepository,
            AiConclusionRepository conclusionRepository) {
        this.taskService = taskService;
        this.taskMainRepository = taskMainRepository;
        this.reportRepository = reportRepository;
        this.pythonClient = pythonClient;
        this.jsonUtils = jsonUtils;
        this.restTemplate = restTemplate;
        this.appProperties = appProperties;
        this.aiNarrativeService = aiNarrativeService;
        this.calcResultRepository = calcResultRepository;
        this.assessResultRepository = assessResultRepository;
        this.forecastResultRepository = forecastResultRepository;
        this.conclusionRepository = conclusionRepository;
    }

    private List<String> getOrCreateSuggestedQuestions(String fullReportMd,
                                                       List<Map<String, String>> conversationMessages) {
        boolean hasHistory = conversationMessages != null && !conversationMessages.isEmpty();
        String module = hasHistory ? "FOLLOWUP_QUESTIONS" : "REPORT_QUESTIONS";

        StringBuilder inputBuilder = new StringBuilder(module + ":");
        inputBuilder.append(fullReportMd.substring(0, Math.min(fullReportMd.length(), 2000)));
        if (!hasHistory) {
            String inputHash = sha256(inputBuilder.toString());
            AiConclusionEntity cached = conclusionRepository
                    .findByModuleAndInputHash(module, inputHash).orElse(null);
            if (cached != null) {
                try {
                    Map<String, Object> map = jsonUtils.toMap(cached.getInsightJson());
                    @SuppressWarnings("unchecked")
                    List<String> questions = (List<String>) map.getOrDefault("questions", List.of());
                    return questions;
                } catch (Exception ignored) { }
            }
        }

        StringBuilder prompt = new StringBuilder("你是一个智能推荐系统。基于股票波动率分析报告以及可选的对话上下文，生成3个用户可能感兴趣的后续追问问题。");
        prompt.append("返回严格的 JSON 格式：{\"questions\": [\"问题1\", \"问题2\", \"问题3\"]}。");
        prompt.append("问题应具有针对性和多样性。");

        String reportSnippet = fullReportMd.length() > 2000 ? fullReportMd.substring(0, 2000) + "..." : fullReportMd;
        StringBuilder userContent = new StringBuilder("报告摘要：\n").append(reportSnippet);

        if (hasHistory) {
            userContent.append("\n\n对话历史：\n");
            for (Map<String, String> msg : conversationMessages) {
                String role = msg.get("role");
                if ("user".equals(role) || "assistant".equals(role)) {
                    userContent.append(role).append(": ").append(msg.get("content")).append("\n");
                }
            }
        } else {
            userContent.append("\n\n（无上下文对话）");
        }

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", prompt.toString()),
                Map.of("role", "user", "content", userContent.toString())
        );

        try {
            String rawJson = callDeepSeek(messages);
            Map<String, Object> parsed = jsonUtils.toMap(rawJson);
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) parsed.getOrDefault("questions", List.of());

            if (!hasHistory && !questions.isEmpty()) {
                AiConclusionEntity entity = new AiConclusionEntity();
                entity.setModule(module);
                entity.setInputHash(sha256(inputBuilder.toString()));
                entity.setInsightJson(rawJson);
                conclusionRepository.save(entity);
            }

            return questions.isEmpty() ? getDefaultQuestions() : questions;
        } catch (Exception e) {
            return getDefaultQuestions();
        }
    }

    private List<String> getDefaultQuestions() {
        return List.of(
                "当前最大的不确定性来自哪些指标？",
                "如果波动率继续上升应该观察什么信号？",
                "后续应该优先跟踪哪些变化？"
        );
    }

    public Map<String, Object> generate(String taskNo, AiGenerateRequest request) {
        requireDeepSeekConfigured();

        TaskMainEntity task = taskService.findTask(taskNo);
        TaskParamSnapshotEntity snapshot = taskService.findSnapshot(task.getId());
        Map<String, Object> payloadRequest = new LinkedHashMap<>(jsonUtils.toMap(snapshot.getParamJson()));
        payloadRequest.put("taskNo", taskNo);
        VolatilityCalcResultEntity calc = calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId())
                .orElseThrow(() -> new BusinessException(5001, "缺少波动率计算数据"));
        VolatilityAssessResultEntity assess = assessResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId())
                .orElseThrow(() -> new BusinessException(5001, "缺少评估数据"));
        VolatilityForecastResultEntity forecast = forecastResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId())
                .orElseThrow(() -> new BusinessException(5001, "缺少预测数据"));

        Map<String, Object> calcMap = jsonUtils.toMap(calc.getResultJson());
        Map<String, Object> assessMap = jsonUtils.toMap(assess.getResultJson());
        Map<String, Object> forecastMap = jsonUtils.toMap(forecast.getResultJson());
        Map<String, Object> donchian = jsonUtils.toMap(assess.getDonchianJson());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stockCode", calc.getStockCode());
        payload.put("stockName", task.getStockName());
        payload.put("analysisPeriod", snapshot.getDateStart() + " ~ " + snapshot.getDateEnd());
        payload.put("historicalVolatility", calc.getYzVolatility());
        payload.put("impliedVolatility", calc.getImpliedVolatility());
        payload.put("impliedVolatilityMethod", calcMap.getOrDefault("impliedVolatilityMethod", "-"));
        payload.put("scoreTotal", assess.getScoreTotal());
        payload.put("scoreStability", assess.getScoreStability());
        payload.put("scoreRisk", assess.getScoreRisk());
        payload.put("riskLevel", assess.getRiskLevel());
        payload.put("qualitativeLabel", assess.getQualitativeLabel());
        payload.put("industryAvgVol", assess.getIndustryAvgVol());
        payload.put("predictVolatility", forecast.getPredictVolatility());
        payload.put("ciLower", forecast.getCiLower());
        payload.put("ciUpper", forecast.getCiUpper());
        payload.put("modelName", forecast.getModelName());
        payload.put("volTrend", inferTrendFromSeries(calcMap));   // 简单的趋势推断方法（见下方）
        payload.put("forecastInterval", String.format("%.2f%% ~ %.2f%%",
                forecast.getCiLower().multiply(BigDecimal.valueOf(100)),
                forecast.getCiUpper().multiply(BigDecimal.valueOf(100))));
        payload.put("confidenceLevelValue", snapshot.getConfidenceLevel().doubleValue());
        payload.put("confidenceLevel", String.format("%.0f%%", snapshot.getConfidenceLevel().doubleValue() * 100));
        payload.put("donchianSummary", buildDonchianSummaryFromMap(donchian));  // 简单文本描述
        payload.put("donchianUpper", donchian.get("upper"));
        payload.put("donchianMiddle", donchian.get("middle"));
        payload.put("donchianLower", donchian.get("lower"));
        payload.put("donchianLatestClose", donchian.get("latestClose"));

        Map<String, Object> analysisInsight = aiNarrativeService.generateAnalysisConclusion(
                Map.of(
                        "historicalVolatility", payload.get("historicalVolatility"),
                        "impliedVolatility", payload.get("impliedVolatility"),
                        "impliedVolatilityMethod", payload.get("impliedVolatilityMethod")
                )
        );
        Map<String, Object> donchianData = new HashMap<>();
        donchianData.put("upper", payload.get("donchianUpper"));
        donchianData.put("middle", payload.get("donchianMiddle"));
        donchianData.put("lower", payload.get("donchianLower"));
        donchianData.put("latestClose", payload.get("donchianLatestClose"));

        Map<String, Object> assessData = new LinkedHashMap<>();
        assessData.put("scoreTotal", payload.get("scoreTotal"));
        assessData.put("scoreStability", payload.get("scoreStability"));
        assessData.put("scoreRisk", payload.get("scoreRisk"));
        assessData.put("riskLevel", payload.get("riskLevel"));
        assessData.put("qualitativeLabel", payload.get("qualitativeLabel"));
        assessData.put("industryAvgVol", payload.get("industryAvgVol"));
        assessData.put("donchian", donchianData);

        Map<String, Object> assessmentInsight = aiNarrativeService.generateAssessmentExplanation(assessData);
        Map<String, Object> forecastInsight = aiNarrativeService.generateForecastExplanation(
                Map.of(
                        "predictVolatility", payload.get("predictVolatility"),
                        "ciLower", payload.get("ciLower"),
                        "ciUpper", payload.get("ciUpper"),
                        "confidenceLevel", payload.get("confidenceLevelValue"),
                        "riskLevel", payload.get("riskLevel"),
                        "modelName", payload.get("modelName"),
                        "isBatch", false,
                        "stockCount", 1
                )
        );

        Map<String, Object> insights = new LinkedHashMap<>();
        insights.put("analysis", analysisInsight);
        insights.put("assessment", assessmentInsight);
        insights.put("forecast", forecastInsight);

        String analysisMode = request.analysisMode() == null ? "PRO" : request.analysisMode().toUpperCase();
        String frontMatter = buildFrontMatter(insights);
        String promptHash = sha256(jsonUtils.toJson(payload) + jsonUtils.toJson(insights) + analysisMode + appProperties.getDeepseekModel());

        AiAnalysisReportEntity existing = reportRepository
                .findFirstByTaskIdAndPromptHashOrderByCreateTimeDesc(task.getId(), promptHash)
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        String systemPrompt = buildSystemPrompt(analysisMode);
        String userPrompt = buildGeneratePrompt(payload, insights, analysisMode);
        String adviceMarkdown = callDeepSeek(List.of(
                message("system", systemPrompt),
                message("user", userPrompt)
        ));
        String reportMarkdown = frontMatter + "\n\n" + normalizeAdviceMarkdown(adviceMarkdown);

        AiAnalysisReportEntity entity = new AiAnalysisReportEntity();
        entity.setTaskId(task.getId());
        entity.setStockCode(task.getStockCode());
        entity.setAnalysisMode(analysisMode);
        entity.setPromptHash(promptHash);
        entity.setInputJson(jsonUtils.toJson(payload));
        entity.setSummaryText(extractSummary(reportMarkdown));
        entity.setFullReportMd(reportMarkdown);
        entity.setRiskDisclaimer(RISK_DISCLAIMER);
        reportRepository.save(entity);

        task.setTaskStatus(TaskStatus.AI_DONE.name());
        task.setProgress(100);
        task.setResultSummary("AI 分析已生成");
        task.setEndTime(LocalDateTime.now());
        taskMainRepository.save(task);

        return toResponse(entity);
    }

    public Map<String, Object> followup(String taskNo, AiFollowupRequest request) {
        requireDeepSeekConfigured();

        TaskMainEntity task = taskService.findTask(taskNo);
        AiAnalysisReportEntity report = reportRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId())
                .orElseThrow(() -> new BusinessException(5001, "请先生成 AI 分析"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", "你是股票波动率分析助手。请基于已有报告和对话上下文回答追问，保持专业、克制、可解释，不承诺收益。"));
        messages.add(message("user", "以下是当前报告内容：\n\n" + report.getFullReportMd()));
        for (AiFollowupRequest.AiFollowupMessage item : recentHistory(request.history())) {
            String role = "assistant".equalsIgnoreCase(item.role()) ? "assistant" : "user";
            if (StringUtils.hasText(item.content())) {
                messages.add(message(role, item.content()));
            }
        }
        messages.add(message("user", request.question()));

        String answer = callDeepSeek(messages);

        List<String> suggested = getOrCreateSuggestedQuestions(report.getFullReportMd(), messages);

        return Map.of(
                "question", request.question(),
                "answer", answer,
                "suggestedQuestions", suggested
        );
    }

    public AiAnalysisReportEntity latestReport(String taskNo) {
        TaskMainEntity task = taskService.findTask(taskNo);
        return reportRepository.findFirstByTaskIdOrderByCreateTimeDesc(task.getId())
                .orElseThrow(() -> new BusinessException(1002, "AI 报告不存在"));
    }


    private void requireDeepSeekConfigured() {
        if (!StringUtils.hasText(appProperties.getDeepseekApiKey())) {
            throw new BusinessException(5001, "未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY 后重试");
        }
    }

    private String callDeepSeek(List<Map<String, String>> messages) {
        String url = appProperties.getDeepseekBaseUrl() + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appProperties.getDeepseekApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", appProperties.getDeepseekModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", appProperties.getDeepseekTemperature());
        requestBody.put("max_tokens", appProperties.getDeepseekMaxTokens());

        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    new ParameterizedTypeReference<>() {});
        } catch (RestClientException ex) {
            throw new BusinessException(5001, "DeepSeek 调用超时或网络异常，请稍后重试");
        }

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new BusinessException(5001, "DeepSeek 返回为空");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException(5001, "DeepSeek 未返回可用结果");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || !StringUtils.hasText(String.valueOf(message.get("content")))) {
            throw new BusinessException(5001, "DeepSeek 返回内容为空");
        }

        String content = String.valueOf(message.get("content")).trim();
        content = cleanDeepSeekResult(content);
        return content;
    }

    private String cleanDeepSeekResult(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int end = cleaned.indexOf("\n");
            if (end > 0) {
                cleaned = cleaned.substring(end + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        cleaned = cleaned.replaceFirst("^(markdown|json)\\s*\\n", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        cleaned = cleaned.strip();
        return cleaned;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private String buildSystemPrompt(String analysisMode) {
        List<String> rules = new ArrayList<>();
        rules.add("你是股票波动率分析系统中的专业分析助手。");
        rules.add("请基于输入的结构化指标撰写报告，不要杜撰不存在的数据。");
        rules.add("报告前三节已经由系统生成，你只需要输出 Markdown，并严格包含：四、操作建议；五、风险提示。");
        rules.add("不要承诺收益、不要给出保本结论、不要使用营销措辞。");
        rules.add("如果数据不足或结论不确定，要明确提示可信度有限。");
        rules.add("风险提示中必须包含这句话：" + RISK_DISCLAIMER);
        if ("BRIEF".equals(analysisMode)) {
            rules.add("输出简洁版，每节控制在 2 到 4 句。");
        } else {
            rules.add("输出专业版，每节内容更完整，但保持条理清晰。");
        }
        return String.join("\n", rules);
    }

    private String buildGeneratePrompt(Map<String, Object> payload, Map<String, Object> insights, String analysisMode) {
        return """
                请根据以下股票波动率分析输入生成报告后半部分。前三节已经生成，请不要重复输出。

                分析模式：%s
                结构化输入：
                %s
                已生成的前三节页面内容：
                %s

                输出要求：
                1. 使用中文 Markdown。
                2. 只输出“## 四、操作建议”和“## 五、风险提示”。
                3. 操作建议需分为保守、中性、激进三类。
                4. 风险提示必须单独成节，且包含免责声明。
                5. 不要重复“一、分析结论”“二、关键指标说明”“三、预测结果说明”。
                """.formatted(analysisMode, jsonUtils.toJson(payload), jsonUtils.toJson(insights));
    }

    private String buildFrontMatter(Map<String, Object> insights) {
        return String.join("\n\n",
                markdownSection(section(insights, "analysis"), 1),
                markdownSection(section(insights, "assessment"), 2),
                markdownSection(section(insights, "forecast"), 3)
        );
    }

    @SuppressWarnings("unchecked")
    private String markdownSection(Map<String, Object> section, int order) {
        List<String> bullets = section.get("bullets") instanceof List<?> raw
                ? raw.stream().map(String::valueOf).toList()
                : List.of();
        StringBuilder builder = new StringBuilder();
        builder.append("## ").append(chineseOrder(order)).append("、").append(section.get("title")).append("\n\n");
        builder.append(section.get("summary")).append("\n\n");
        for (String bullet : bullets) {
            builder.append("- ").append(bullet).append("\n");
        }
        return builder.toString().trim();
    }

    private String chineseOrder(int order) {
        return switch (order) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            default -> String.valueOf(order);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> insights, String key) {
        Object section = insights.get(key);
        return section instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private String normalizeAdviceMarkdown(String markdown) {
        String trimmed = markdown == null ? "" : markdown.trim();
        int fourth = trimmed.indexOf("## 四、");
        if (fourth >= 0) {
            return trimmed.substring(fourth).trim();
        }
        return trimmed;
    }

    private List<AiFollowupRequest.AiFollowupMessage> recentHistory(List<AiFollowupRequest.AiFollowupMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<AiFollowupRequest.AiFollowupMessage> valid = history.stream()
                .filter(item -> item != null && StringUtils.hasText(item.content()))
                .toList();
        return valid.size() <= 8 ? valid : valid.subList(valid.size() - 8, valid.size());
    }

    private String extractSummary(String markdown) {
        String[] lines = markdown.split("\\R");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(trimmed);
            if (builder.length() >= 120) {
                break;
            }
        }
        return builder.toString().trim();
    }

    public Map<String, Object> toResponse(AiAnalysisReportEntity entity) {
        List<String> questions = getOrCreateSuggestedQuestions(
                entity.getFullReportMd(), null);  // 无对话
        return Map.of(
                "analysisMode", entity.getAnalysisMode(),
                "summaryText", entity.getSummaryText(),
                "fullReportMd", entity.getFullReportMd(),
                "riskDisclaimer", entity.getRiskDisclaimer(),
                "input", jsonUtils.toMap(entity.getInputJson()),
                "insights", Map.of(),
                "suggestedQuestions", questions
        );
    }
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("生成摘要失败", ex);
        }
    }
    private String inferTrendFromSeries(Map<String, Object> calcMap) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) calcMap.getOrDefault("trendSeries", List.of());
        if (trend.size() < 2) return "走势平稳";
        double last = ((Number) trend.get(trend.size() - 1).get("value")).doubleValue();
        double first = ((Number) trend.get(0).get("value")).doubleValue();
        double diff = last - first;
        if (diff > 0.03) return "近期明显上行";
        if (diff > 0.01) return "近期缓慢上行";
        if (diff < -0.03) return "近期明显回落";
        if (diff < -0.01) return "近期缓慢回落";
        return "走势平稳";
    }

    private String buildDonchianSummaryFromMap(Map<String, Object> donchian) {
        double latestClose = ((Number) donchian.getOrDefault("latestClose", 0)).doubleValue();
        double upper = ((Number) donchian.getOrDefault("upper", 0)).doubleValue();
        double lower = ((Number) donchian.getOrDefault("lower", 0)).doubleValue();
        double middle = ((Number) donchian.getOrDefault("middle", 0)).doubleValue();
        if (latestClose >= upper) return "当前价格接近上轨，存在突破后的放大波动风险";
        if (latestClose <= lower) return "当前价格靠近下轨，需关注下行波动放大";
        if (latestClose >= middle) return "当前价格位于中上轨之间，未出现明显突破";
        return "当前价格位于中下轨之间，整体仍在通道内运行";
    }
}