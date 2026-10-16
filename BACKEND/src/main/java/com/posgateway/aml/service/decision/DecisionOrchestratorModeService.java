package com.posgateway.aml.service.decision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the active {@code hokeka.decision.orchestrator} mode and allows operators to flip it at
 * runtime without restarting the Control Plane (legacy / shadow / live).
 */
@Service
public class DecisionOrchestratorModeService {

    private static final Set<String> ALLOWED = Set.of("legacy", "shadow", "live");

    private final AtomicReference<String> mode = new AtomicReference<>();

    public DecisionOrchestratorModeService(
            @Value("${hokeka.decision.orchestrator:shadow}") String initialMode) {
        this.mode.set(normalize(initialMode));
    }

    /** Returns the current orchestrator mode used by {@link com.posgateway.aml.service.DecisionEngine}. */
    public String getMode() {
        return mode.get();
    }

    /**
     * Updates orchestrator mode at runtime; throws if the token is not legacy, shadow, or live.
     */
    public String setMode(String nextMode) {
        String normalized = normalize(nextMode);
        mode.set(normalized);
        return normalized;
    }

    /** Normalizes and validates orchestrator mode tokens from env or operator API calls. */
    private String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Orchestrator mode is required");
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(token)) {
            throw new IllegalArgumentException(
                    "Orchestrator mode must be one of legacy, shadow, live (got: " + raw + ")");
        }
        return token;
    }
}
