package com.posgateway.aml.model;

import java.util.Locale;

/** Canonical transaction outcomes used by scoring, monitoring, and reporting. */
public enum TransactionDecision {
    ALLOW,
    ALERT,
    HOLD,
    BLOCK;

    public static TransactionDecision fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ALLOW", "APPROVED" -> ALLOW;
            case "ALERT", "FLAG", "FLAGGED" -> ALERT;
            case "HOLD", "REVIEW", "MANUAL_REVIEW", "UNDER_REVIEW" -> HOLD;
            case "BLOCK", "DECLINED", "REJECTED", "SUSPENDED" -> BLOCK;
            default -> throw new IllegalArgumentException("Unknown transaction decision: " + value);
        };
    }

    public static TransactionDecision fromRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return ALLOW;
        }
        return switch (riskLevel.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> BLOCK;
            case "HIGH" -> HOLD;
            case "MEDIUM" -> ALERT;
            default -> ALLOW;
        };
    }

    public static TransactionDecision strongest(TransactionDecision left, TransactionDecision right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    public boolean isReviewRequired() {
        return this == ALERT || this == HOLD || this == BLOCK;
    }
}
