package com.posgateway.aml.service.jev;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Versioned prompt templates stored under {@code classpath:jev/prompts/}.
 */
@Service
public class JevPromptTemplateService {

    private static final String SHARED_SCHEMA = """
            Respond with JSON ONLY — no markdown fences, no prose. Schema:
            {
              "recommendation": "APPROVE|REVIEW|DECLINE|ESCALATE",
              "riskScore": number 0-100,
              "confidence": number 0-1,
              "reasons": ["string"],
              "citedSignals": ["string"]
            }
            """;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String resolveSystemPrompt(JevEngineType engine, String version) {
        String key = engine.name() + "/" + version;
        return cache.computeIfAbsent(key, k -> loadTemplate(engine, version));
    }

    private String loadTemplate(JevEngineType engine, String version) {
        String path = "jev/prompts/" + engine.name().toLowerCase() + "/" + version + ".txt";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8) + "\n\n" + SHARED_SCHEMA;
            }
        } catch (IOException ignored) {
            // fall through to default
        }
        return defaultPrompt(engine) + "\n\n" + SHARED_SCHEMA;
    }

    private static String defaultPrompt(JevEngineType engine) {
        return switch (engine) {
            case TRANSACTION_RISK -> """
                    You are JEV, Hokeka's AML transaction risk advisor. Review borderline transaction
                    features after deterministic rules. Recommend APPROVE, REVIEW, DECLINE, or ESCALATE.
                    Never override a sanctions BLOCK — sanctions hits always require human review.
                    """;
            case ALERT_TRIAGE -> """
                    You are JEV, Hokeka's alert triage assistant. Prioritize alerts for analysts:
                    suggest priority, summary, and recommended action. Output is advisory only.
                    """;
            case CASE_TRIAGE -> """
                    You are JEV, Hokeka's case triage assistant. Summarize case context and recommend
                    next investigative steps. Output is advisory — analysts decide outcomes.
                    """;
            case SANCTIONS_DISAMBIGUATION -> """
                    You are JEV, Hokeka's sanctions/PEP match disambiguation assistant. Assess whether
                    a match is likely true positive or false positive. Strong matches must NEVER be
                    auto-cleared — always ESCALATE or REVIEW for human decision.
                    """;
            case KYC_EDD -> """
                    You are JEV, Hokeka's KYC/EDD review assistant. Summarize identity evidence gaps
                    and recommend enhanced due diligence steps. Advisory only — no auto-approval.
                    """;
            case G2_CONTENT -> """
                    You are JEV, Hokeka's merchant website/content monitoring assistant. Assess URL/MCC/
                    site-scan signals for transaction laundering or undeclared business lines.
                    """;
            case ADVERSE_MEDIA -> """
                    You are JEV, Hokeka's adverse media relevance scorer. Rate GDELT/article relevance
                    to the subject entity for AML investigation. Advisory only.
                    """;
            case RULE_SUGGESTION -> """
                    You are JEV, Hokeka's AML rule suggestion assistant. Generate ONE SpEL rule definition
                    from the operator prompt. Rules require admin approval before enabling.
                    Additional fields in JSON: name, description, ruleType, ruleExpression, severity,
                    action, score, priority.
                    """;
            case FRAUD_SCORING -> """
                    You are JEV, Hokeka's fraud scoring advisor. Interpret ML/rule signals for post-scoring
                    review when deterministic outcome is borderline. Advisory only.
                    """;
            case SAR_NARRATIVE_VERIFICATION -> """
                    Jev typed decisions handle SAR narrative verification; this prompt is unused.
                    """;
        };
    }
}
