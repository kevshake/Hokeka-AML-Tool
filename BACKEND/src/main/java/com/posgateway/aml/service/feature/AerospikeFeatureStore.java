package com.posgateway.aml.service.feature;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory feature store that mirrors the Aerospike interface.
 * Aerospike is not available in the test/build environment; data is held in
 * ConcurrentHashMaps scoped to the JVM lifetime (appropriate for test/UAT).
 */
@Service
public class AerospikeFeatureStore {

    private final ConcurrentHashMap<String, Map<String, Object>> featureStore  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>> counterStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> riskScoreStore = new ConcurrentHashMap<>();

    public void storeFeature(String key, String binName, Object value) {
        featureStore.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(binName, value);
    }

    public Object getFeature(String key, String binName) {
        Map<String, Object> bins = featureStore.get(key);
        return bins != null ? bins.get(binName) : null;
    }

    public Map<String, Object> getAllFeatures(String key) {
        return featureStore.getOrDefault(key, new HashMap<>());
    }

    public void incrementCounter(String key, String binName, int value) {
        counterStore.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(binName, b -> new AtomicLong(0))
                    .addAndGet(value);
    }

    public long getCounter(String key, String binName) {
        ConcurrentHashMap<String, AtomicLong> counters = counterStore.get(key);
        if (counters == null) return 0L;
        AtomicLong c = counters.get(binName);
        return c != null ? c.get() : 0L;
    }

    public void storeRiskScore(String entityId, double score, String riskType) {
        Map<String, Object> entry = new ConcurrentHashMap<>();
        entry.put("score", score);
        entry.put("riskType", riskType);
        entry.put("lastUpdated", System.currentTimeMillis());
        riskScoreStore.put(entityId, entry);
    }
}
