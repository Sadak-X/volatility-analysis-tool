package com.volatility.modules.task;

import com.volatility.common.config.AppProperties;
import com.volatility.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PythonClient {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public PythonClient(RestTemplate restTemplate, AppProperties appProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> post(String path, Object body) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    appProperties.getPythonBaseUrl() + path,
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw new BusinessException(3001, pythonErrorMessage(ex));
        }
    }

    public Map<String, Object> get(String path) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    appProperties.getPythonBaseUrl() + path,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw new BusinessException(3001, pythonErrorMessage(ex));
        }
    }

    public Map<String, Object> get(String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(appProperties.getPythonBaseUrl() + path);
        if (queryParams != null && !queryParams.isEmpty()) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
        URI uri = builder.build().encode().toUri();
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw new BusinessException(3001, pythonErrorMessage(ex));
        }
    }

    private String pythonErrorMessage(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "Python 服务调用失败：" + ex.getStatusCode();
        }
        try {
            Map<String, Object> error = objectMapper.readValue(body, new TypeReference<>() {});
            Object detail = error.get("detail");
            if (detail != null) {
                return String.valueOf(detail);
            }
        } catch (Exception ignored) {
            // Fall through to the raw body.
        }
        return body;
    }
}
