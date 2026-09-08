package com.volatility.common.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String jwtSecret;
    private long jwtExpireHours;
    private String pythonBaseUrl;
    private String uploadPath = "./uploads";
    private String deepseekBaseUrl;
    private String deepseekApiKey;
    private String deepseekModel;
    private Double deepseekTemperature = 0.4;
    private Integer deepseekMaxTokens = 3000;
    private Integer deepseekConnectTimeoutMs = 10_000;
    private Integer deepseekReadTimeoutMs = 300_000;
    private List<String> defaultChartSpans = new ArrayList<>();
    private BigDecimal defaultConfidenceLevel = BigDecimal.valueOf(0.90);
}
