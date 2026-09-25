package com.posgateway.aml.service.jev;

/**
 * Structured AI recommendation. Engines map these to their native action vocabulary.
 */
public enum JevRecommendation {
    APPROVE,
    REVIEW,
    DECLINE,
    ESCALATE;

    public static JevRecommendation fromString(String value) {
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
