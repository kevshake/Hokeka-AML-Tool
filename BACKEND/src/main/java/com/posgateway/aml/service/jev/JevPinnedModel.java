package com.posgateway.aml.service.jev;

/**
 * Laya routing hints for {@code POST /v1/systemone}. Unknown legacy Jev ids are ignored by Laya.
 */
public final class JevPinnedModel {

    /** Automatic routing when unset (Laya picks English vs multilingual). */
    public static final String AUTO_ROUTE = "";

    private JevPinnedModel() {
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
