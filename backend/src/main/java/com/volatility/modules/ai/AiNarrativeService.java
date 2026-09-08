package com.volatility.modules.ai;

import com.volatility.common.config.AppProperties;
import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.entity.AiConclusionEntity;
import com.volatility.modules.ai.repository.AiConclusionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiNarrativeService {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;
    private final JsonUtils jsonUtils;
    private final AiConclusionRepository conclusionRepository;

    public AiNarrativeService(@Qualifier("deepSeekRestTemplate") RestTemplate restTemplate,
                              AppProperties appProperties,
                              JsonUtils jsonUtils,
                              AiConclusionRepository conclusionRepository) {
        this.restTemplate = restTemplate;
        this.appProperties = appProperties;
        this.jsonUtils = jsonUtils;
        this.conclusionRepository = conclusionRepository;
    }


    private Map<String, Object> generateNarrativeCached(String module,
                                                        Map<String, Object> inputData,
                                                        String systemPrompt,
                                                        String userPromptTemplate) {

        String inputJson = jsonUtils.toJson(inputData);
        String inputHash = sha256(module + ":" + inputJson);

        AiConclusionEntity cached = conclusionRepository.findByModuleAndInputHash(module, inputHash).orElse(null);
        if (cached != null) {
            return jsonUtils.toMap(cached.getInsightJson());
        }

        requireDeepSeekConfigured();
        String userPrompt = userPromptTemplate.replace("{data}", inputJson);
        Map<String, String> systemMsg = Map.of("role", "system", "content", systemPrompt);
        Map<String, String> userMsg = Map.of("role", "user", "content", userPrompt);
        String rawJson = callDeepSeek(List.of(systemMsg, userMsg));

        AiConclusionEntity entity = new AiConclusionEntity();
        entity.setModule(module);
        entity.setInputHash(inputHash);
        entity.setInsightJson(rawJson);
        conclusionRepository.save(entity);

        return jsonUtils.toMap(rawJson);
    }


    public Map<String, Object> generateAnalysisConclusion(Map<String, Object> data) {
        String system = "你是一个股票波动率分析专家。请根据提供的数据生成分析结论。"
                + "返回严格的 JSON 格式，包含 title (字符串), summary (字符串), bullets (字符串数组)。";
        String userTemplate = "下面是波动率计算结果分析：\n{data}\n请生成分析结论，包括标题、一段总结和至少3个要点。使用中文，且标题为波动率计算结果分析";
        return generateNarrativeCached("ANALYSIS", data, system, userTemplate);
    }

    public Map<String, Object> generateAssessmentExplanation(Map<String, Object> data) {
        String system = "你是一个股票波动率分析专家。请根据评估数据生成解释。重点分析一下唐奇安通道、风险评分和稳定性"
                + "返回严格的 JSON 格式，包含 title, summary, bullets。";
        String userTemplate = "评估数据：\n{data}\n其中包括唐奇安通道数据（donchian），请根据唐奇安通道数据解释为什么会有其展示的趋势变化。"
                + "请生成评估解释，包括标题、一段总结和至少3个要点。使用中文，且标题为波动率评估结果分析";
        return generateNarrativeCached("ASSESSMENT", data, system, userTemplate);
    }

    public Map<String, Object> generateForecastExplanation(Map<String, Object> data) {
        String system = "你是一个波动率预测专家。请根据预测数据生成解释说明。"
                + "返回严格的 JSON 格式，包含 title, summary, bullets。";
        String userTemplate = "预测数据：\n{data}\n请生成预测说明，包括标题、一段总结和至少3个要点。使用中文，且标题为波动率预测结果分析";
        return generateNarrativeCached("FORECAST", data, system, userTemplate);
    }

    private void requireDeepSeekConfigured() {
        if (!StringUtils.hasText(appProperties.getDeepseekApiKey())) {
            throw new BusinessException(5001, "未配置 DeepSeek API Key");
        }
    }

    private String callDeepSeek(List<Map<String, String>> messages) {
        String url = appProperties.getDeepseekBaseUrl() + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appProperties.getDeepseekApiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", appProperties.getDeepseekModel());
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 800);
        body.put("response_format", Map.of("type", "json_object"));

        var response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> respBody = response.getBody();
        if (respBody == null) throw new BusinessException(5001, "DeepSeek 返回为空");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
        if (choices == null || choices.isEmpty()) throw new BusinessException(5001, "DeepSeek 未返回结果");

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) throw new BusinessException(5001, "消息为空");

        String content = String.valueOf(message.get("content")).trim();
        if (!StringUtils.hasText(content)) throw new BusinessException(5001, "内容为空");

        return content;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}