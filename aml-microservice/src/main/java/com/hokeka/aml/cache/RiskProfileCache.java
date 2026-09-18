package com.hokeka.aml.cache;

import com.aeroorm.AeroEntity;
import com.aeroorm.AeroKey;
import com.aeroorm.AeroRepository;

/**
 * Typed marker for AeroORM risk_profile set. Map payloads are stored via {@link AeroRepository#saveMap}.
 */
@AeroEntity(set = "risk_profile")
public class RiskProfileCache {
    @AeroKey
    private String key;

    public RiskProfileCache() {
    }

    public RiskProfileCache(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
