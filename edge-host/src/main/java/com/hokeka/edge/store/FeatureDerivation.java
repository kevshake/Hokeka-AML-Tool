package com.hokeka.edge.store;

import java.util.Map;

/**
 * Result of reading locally-derived velocity features, including whether the store was unavailable.
 */
public record FeatureDerivation(Map<String, Object> features, boolean storeUnavailable) {

    public FeatureDerivation {
        features = features == null ? Map.of() : features;
    }
}
