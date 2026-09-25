package com.posgateway.aml.service.jev;

/**
 * Pinned TypeSafe Jev model for the OpenRouter Decisions API. Not overridable to arbitrary chat models.
 */
public final class JevPinnedModel {

    public static final String MODEL_ID = "typesafe/jev-1.13";
    public static final String SNAPSHOT_PREFIX = "typesafe/jev-1.13-";

    private JevPinnedModel() {
    }

    public static boolean isValidSnapshot(String snapshot) {
        return snapshot != null && snapshot.startsWith(SNAPSHOT_PREFIX);
    }
}
