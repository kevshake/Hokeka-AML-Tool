package com.posgateway.aml.service.ai.decision;

/**
 * Structured AI recommendation. Engines map these to their native action vocabulary.
 */
public enum AiRecommendation {
    APPROVE,
    REVIEW,
    DECLINE,
    ESCALATE;

    public static AiRecommendation fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "APPROVE", "ALLOW", "ACCEPT" -> APPROVE;
            case "REVIEW", "ALERT", "HOLD" -> REVIEW;
            case "DECLINE", "BLOCK", "REJECT", "DENY" -> DECLINE;
            case "ESCALATE", "ESCALATION" -> ESCALATE;
            default -> null;
        };
    }
}
