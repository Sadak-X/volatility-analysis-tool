package com.volatility.modules.ai;

import com.volatility.common.config.AppProperties;
import com.volatility.common.exception.BusinessException;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.ai.dto.AiFollowupRequest;
import com.volatility.modules.ai.entity.AiAnalysisReportEntity;
import com.volatility.modules.ai.entity.AiConclusionEntity;
import com.volatility.modules.ai.repository.AiAnalysisReportRepository;
import com.volatility.modules.ai.repository.AiConclusionRepository;
import com.volatility.modules.analysis.repository.VolatilityCalcResultRepository;
import com.volatility.modules.assessment.repository.VolatilityAssessResultRepository;
import com.volatility.modules.forecast.repository.VolatilityForecastResultRepository;
import com.volatility.modules.task.TaskService;
import com.volatility.modules.task.entity.TaskMainEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private AppProperties appProperties;
    @Mock private JsonUtils jsonUtils;
    @Mock private TaskService taskService;
    @Mock private AiAnalysisReportRepository reportRepository;
    @Mock private VolatilityCalcResultRepository calcResultRepository;
    @Mock private VolatilityAssessResultRepository assessResultRepository;
    @Mock private VolatilityForecastResultRepository forecastResultRepository;
    @Mock private AiConclusionRepository conclusionRepository;

    @InjectMocks private AiService aiService;
    @InjectMocks private AiNarrativeService aiNarrativeService;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.getDeepseekBaseUrl()).thenReturn("https://api.deepseek.com");
        lenient().when(appProperties.getDeepseekModel()).thenReturn("deepseek-chat");
        lenient().when(appProperties.getDeepseekApiKey()).thenReturn("sk-test-key");
    }

    @Test
    void TC_AI_01_TestAntiJailbreakPrompt() {
        String analysisMode = "PRO";
        String systemPrompt = ReflectionTestUtils.invokeMethod(aiService, "buildSystemPrompt", analysisMode);

        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("你是股票波动率分析系统中的专业分析助手"));
        assertTrue(systemPrompt.contains("请基于输入的结构化指标撰写报告，不要杜撰不存在的数据"));
    }

    @Test
    void TC_AI_02_TestRiskDisclaimer() {
        AiAnalysisReportEntity entity = new AiAnalysisReportEntity();
        entity.setRiskDisclaimer("投资有风险，分析结果仅供研究参考，不构成投资建议。");
        assertEquals("投资有风险，分析结果仅供研究参考，不构成投资建议。", entity.getRiskDisclaimer());
    }

    @Test
    void TC_AI_03_TestNoProfitPromise() {
        String analysisMode = "PRO";
        String systemPrompt = ReflectionTestUtils.invokeMethod(aiService, "buildSystemPrompt", analysisMode);

        assertTrue(systemPrompt.contains("不要承诺收益"));
        assertTrue(systemPrompt.contains("不要给出保本结论"));
        assertTrue(systemPrompt.contains("不要使用营销措辞"));
    }

    @Test
    void TC_AI_04_TestMissingApiKeyThrowsException() {
        when(appProperties.getDeepseekApiKey()).thenReturn("");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            ReflectionTestUtils.invokeMethod(aiService, "requireDeepSeekConfigured");
        });

        assertEquals(5001, ex.getCode());
        assertTrue(ex.getMessage().contains("未配置 DeepSeek API Key"));
    }

    @Test
    void TC_AI_05_TestApiTimeout() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("Connection timed out"));

        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "hi"));

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            ReflectionTestUtils.invokeMethod(aiService, "callDeepSeek", messages);
        });

        assertTrue(ex.getMessage().contains("DeepSeek 调用超时或网络异常，请稍后重试"));
    }

    @Test
    void TC_AI_06_TestCleanDeepSeekResult() {
        String dirtyJson = "```json\n{\"title\": \"分析结论\"}\n```";
        String cleaned = ReflectionTestUtils.invokeMethod(aiService, "cleanDeepSeekResult", dirtyJson);
        assertEquals("{\"title\": \"分析结论\"}", cleaned);
    }

    @Test
    void TC_AI_07_TestEmptyApiResponse() {
        Map<String, Object> body = new HashMap<>();
        body.put("choices", Collections.emptyList());
        ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "test"));

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            ReflectionTestUtils.invokeMethod(aiService, "callDeepSeek", messages);
        });

        assertTrue(ex.getMessage().contains("DeepSeek 未返回可用结果"));
    }

    @Test
    void TC_AI_08_TestCacheHitPreventsApiCall() {
        String module = "ANALYSIS";
        Map<String, Object> inputData = Map.of("data", "test");
        AiConclusionEntity cachedEntity = new AiConclusionEntity();
        cachedEntity.setInsightJson("{\"result\": \"hit\"}");

        when(jsonUtils.toJson(inputData)).thenReturn("{\"data\":\"test\"}");
        when(conclusionRepository.findByModuleAndInputHash(eq(module), anyString()))
                .thenReturn(Optional.of(cachedEntity));
        when(jsonUtils.toMap(anyString())).thenReturn(Map.of("result", "hit"));

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(aiNarrativeService,
                "generateNarrativeCached", module, inputData, "sys", "user");

        assertEquals("hit", result.get("result"));
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), any(Class.class));
    }

    @Test
    void TC_AI_09_TestBriefModeInstructions() {
        String analysisMode = "BRIEF";
        String systemPrompt = ReflectionTestUtils.invokeMethod(aiService, "buildSystemPrompt", analysisMode);
        assertTrue(systemPrompt.contains("输出简洁版，每节控制在 2 到 4 句"));
    }

    @Test
    void TC_AI_10_TestSuggestedQuestionsFallbackOnParseError() {
        // 1. Mock 大模型接口，假装它成功返回了一段文本（比如乱码）
        Map<String, Object> mockMessage = Map.of("content", "some invalid json string");
        Map<String, Object> mockChoice = Map.of("message", mockMessage);
        Map<String, Object> mockBody = Map.of("choices", List.of(mockChoice));
        ResponseEntity<Map<String, Object>> mockResponse = ResponseEntity.ok(mockBody);

        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        // 2. 此时代码才会真正走到 jsonUtils.toMap，我们模拟它解析失败抛出异常
        when(jsonUtils.toMap(anyString())).thenThrow(new RuntimeException("JSON Parse Error"));

        // 3. 执行方法
        List<String> questions = ReflectionTestUtils.invokeMethod(aiService, "getOrCreateSuggestedQuestions", "report text", null);

        // 4. 验证是否触发了 catch 代码块里的降级默认问题
        assertNotNull(questions);
        assertEquals(3, questions.size());
        assertTrue(questions.contains("当前最大的不确定性来自哪些指标？"));
    }

    @Test
    void TC_AI_11_TestRecentHistoryTruncation() {
        List<AiFollowupRequest.AiFollowupMessage> longHistory = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            longHistory.add(new AiFollowupRequest.AiFollowupMessage("user", "msg " + i));
        }

        List<AiFollowupRequest.AiFollowupMessage> truncated = ReflectionTestUtils.invokeMethod(aiService, "recentHistory", longHistory);

        assertEquals(8, truncated.size());
        assertEquals("msg 7", truncated.get(0).content());
        assertEquals("msg 14", truncated.get(7).content());
    }

    @Test
    void TC_AI_12_TestMissingPreconditionDataBlocksGeneration() {
        String taskNo = "TASK-001";
        TaskMainEntity task = new TaskMainEntity();
        task.setId(1L);
        when(taskService.findTask(taskNo)).thenReturn(task);
        when(taskService.findSnapshot(1L)).thenReturn(new com.volatility.modules.task.entity.TaskParamSnapshotEntity());
        when(calcResultRepository.findFirstByTaskIdOrderByCreateTimeDesc(1L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            aiService.generate(taskNo, new com.volatility.modules.ai.dto.AiGenerateRequest("PRO"));
        });

        assertTrue(ex.getMessage().contains("缺少波动率计算数据"));
    }
}