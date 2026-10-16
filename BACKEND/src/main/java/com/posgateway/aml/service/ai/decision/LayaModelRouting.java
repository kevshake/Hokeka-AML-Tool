package com.posgateway.aml.service.ai.decision;

/**
 * Laya routing hints for {@code POST /v1/systemone}. Unknown legacy Jev ids are ignored by Laya.
 */
public final class LayaModelRouting {

    /** Automatic routing when unset (Laya picks English vs multilingual). */
    public static final String AUTO_ROUTE = "";

    private LayaModelRouting() {
    }

    public static String requestModel(String configuredModel) {
        if (configuredModel == null || configuredModel.isBlank()) {
            return null;
        }
        return configuredModel.trim();
    }

    public static boolean isValidResponseModel(String model) {
        return model != null && !model.isBlank();
    }
}
