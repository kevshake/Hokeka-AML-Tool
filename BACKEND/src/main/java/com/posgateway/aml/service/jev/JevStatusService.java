package com.posgateway.aml.service.jev;

import com.posgateway.aml.config.jev.JevProperties;
import com.posgateway.aml.repository.jev.JevDecisionAuditRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health/status for JEV without exposing the API key.
 */
@Service
public class JevStatusService {

    private final JevProperties properties;
    private final JevQuestionConfigService questionConfigService;
    private final JevDecisionAuditRepository auditRepository;
    private final JevEngineConfigService engineConfigService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public JevStatusService(JevProperties properties,
                            JevQuestionConfigService questionConfigService,
                            JevDecisionAuditRepository auditRepository,
                            JevEngineConfigService engineConfigService,
                            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.properties = properties;
        this.questionConfigService = questionConfigService;
        this.auditRepository = auditRepository;
        this.engineConfigService = engineConfigService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", properties.isConfigured());
        status.put("pinnedModel", properties.isConfigured() ? JevPinnedModel.MODEL_ID : null);
        status.put("questionConfigVersion", questionConfigService.getVersion());
        status.put("shadowMode", properties.isShadowMode());
        status.put("promoted", properties.isPromoted());
        status.put("active", properties.isActive());
        status.put("decisionsBaseUrl", properties.getDecisionsBaseUrl());
        status.put("decisionsTimeoutMs", properties.getDecisionsTimeout().toMillis());
        status.put("chatModel", blankToNull(properties.getChatModel()));
        status.put("chatBaseUrl", properties.getChatBaseUrl());
        status.put("timeoutMs", properties.getTimeout().toMillis());
        status.put("inlineTimeoutMs", properties.getInlineTimeout().toMillis());
        status.put("dailyCallBudgetPerPsp", properties.getDailyCallBudgetPerPsp());
        status.put("dailySpendCapUsdPerPsp", properties.getDailySpendCapUsdPerPsp());
        status.put("bandsVersion", properties.getBandsVersion());

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("jevOpenRouter");
        status.put("circuitState", breaker.getState().name());
        status.put("circuitFailureRate", breaker.getMetrics().getFailureRate());

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        status.put("callsLast24h", auditRepository.countCallsSince(since));
        status.put("fallbacksLast24h", auditRepository.countFallbacksSince(since));
        status.put("avgLatencyMsLast24h", auditRepository.averageLatencySince(since).orElse(null));
        status.put("spendLast24hUsd", auditRepository.totalSpendSince(since).orElse(BigDecimal.ZERO));

        status.put("engines", engineConfigService.listAll().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("engineCode", e.getEngineCode());
            row.put("enabled", e.isEnabled());
            row.put("advisoryOnly", e.isAdvisoryOnly());
            row.put("promptVersion", e.getPromptVersion());
            return row;
        }).toList());

        return status;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
