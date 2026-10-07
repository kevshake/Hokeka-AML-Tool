package com.posgateway.aml.service.ai.decision;

import com.posgateway.aml.config.ai.AiDecisionProperties;
import com.posgateway.aml.repository.ai.AiDecisionAuditRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health/status for the Hokeka AI (Laya) layer without exposing the API key.
 */
@Service
public class AiStatusService {

    private final AiDecisionProperties properties;
    private final AiQuestionConfigService questionConfigService;
    private final AiDecisionAuditRepository auditRepository;
    private final AiEngineConfigService engineConfigService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public AiStatusService(AiDecisionProperties properties,
                            AiQuestionConfigService questionConfigService,
                            AiDecisionAuditRepository auditRepository,
                            AiEngineConfigService engineConfigService,
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
        status.put("provider", "laya");
        status.put("model", properties.isConfigured() ? configuredModelLabel() : null);
        status.put("pinnedModel", properties.isConfigured() ? configuredModelLabel() : null);
        status.put("questionConfigVersion", questionConfigService.getVersion());
        status.put("shadowMode", properties.isShadowMode());
        status.put("promoted", properties.isPromoted());
        status.put("active", properties.isActive());
        status.put("apiBaseUrl", properties.getApiBaseUrl());
        status.put("decisionsBaseUrl", properties.getApiBaseUrl());
        status.put("decisionsTimeoutMs", properties.getDecisionsTimeout().toMillis());
        status.put("inlineTimeoutMs", properties.getInlineTimeout().toMillis());
        status.put("minConfidenceToApply", properties.getMinConfidenceToApply());
        status.put("minConfidenceToAutoAct", properties.getMinConfidenceToAutoAct());
        status.put("lang", blankToNull(properties.getLang()));
        status.put("dailyCallBudgetPerPsp", properties.getDailyCallBudgetPerPsp());
        status.put("dailyInputTokenCapPerPsp", properties.getDailyInputTokenCapPerPsp());
        status.put("bandsVersion", properties.getBandsVersion());

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("layaSystemOne");
        status.put("circuitState", breaker.getState().name());
        status.put("circuitFailureRate", breaker.getMetrics().getFailureRate());

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        status.put("callsLast24h", auditRepository.countCallsSince(since));
        status.put("fallbacksLast24h", auditRepository.countFallbacksSince(since));
        status.put("avgLatencyMsLast24h", auditRepository.averageLatencySince(since).orElse(null));
        status.put("inputTokensLast24h", auditRepository.totalInputTokensSince(since).orElse(0L));
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

    private String configuredModelLabel() {
        String pinned = properties.pinnedModel();
        return pinned != null && !pinned.isBlank() ? pinned : "auto";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
