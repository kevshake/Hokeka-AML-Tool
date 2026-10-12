package com.posgateway.aml.service.decision;

/**
 * Target-model decision vocabulary (BLOCK &gt; SUSPEND &gt; FLAG &gt; ALLOW).
 */
public enum CanonicalDecision {
    ALLOW,
    FLAG,
    SUSPEND,
    BLOCK;

    /**
     * Maps legacy engine actions (ALLOW/ALERT/HOLD/BLOCK/REVIEW/FLAG/SUSPEND) to canonical values.
     */
    public static CanonicalDecision fromLegacyAction(String action) {
        if (action == null || action.isBlank()) {
            return ALLOW;
        }
        return switch (action.trim().toUpperCase()) {
            case "BLOCK" -> BLOCK;
            case "HOLD", "SUSPEND" -> SUSPEND;
            case "ALERT", "FLAG", "REVIEW" -> FLAG;
            default -> ALLOW;
        };
    }

    /**
     * Maps canonical decisions back to legacy transaction/alert action strings for one release.
     */
    public static String toLegacyAction(CanonicalDecision decision) {
        if (decision == null) {
            return "ALLOW";
        }
        return switch (decision) {
            case BLOCK -> "BLOCK";
            case SUSPEND -> "HOLD";
            case FLAG -> "ALERT";
            case ALLOW -> "ALLOW";
        };
    }

    /**
     * Returns true when {@code candidate} is strictly stronger than {@code current} in precedence order.
     */
    public static boolean isStronger(CanonicalDecision current, CanonicalDecision candidate) {
        return rank(candidate) > rank(current);
    }

    /**
     * Merges two legacy action strings using canonical precedence, returning a legacy action string.
     */
    public static String mergeLegacyActions(String current, String candidate) {
        CanonicalDecision merged = fromLegacyAction(current);
        CanonicalDecision next = fromLegacyAction(candidate);
        if (isStronger(merged, next)) {
            merged = next;
        }
        return toLegacyAction(merged);
    }

    private static int rank(CanonicalDecision decision) {
        return switch (decision) {
            case BLOCK -> 4;
            case SUSPEND -> 3;
            case FLAG -> 2;
            case ALLOW -> 1;
        };
    }
}
