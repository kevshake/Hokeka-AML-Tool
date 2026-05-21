package com.posgateway.aml.service.feature;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Aerospike Feature Store
 * High-speed storage and retrieval of transaction features, velocity counters, and risk scores.
 */
@Service
public class AerospikeFeatureStore {

    private final AerospikeClient client;
    private final String namespace;
    private final WritePolicy writePolicy;

    public AerospikeFeatureStore(
            AerospikeClient client,
            @Value("${aerospike.namespace:features}") String namespace) {
        this.client = client;
        this.namespace = namespace;
        this.writePolicy = new WritePolicy();
        this.writePolicy.expiration = 86400; // 24 hours default TTL
    }

    public void storeFeature(String key, String binName, Object value) {
        Key aerospikeKey = new Key(namespace, "features", key);
        Bin bin = new Bin(binName, value);
        client.put(writePolicy, aerospikeKey, bin);
    }

    public Object getFeature(String key, String binName) {
        Key aerospikeKey = new Key(namespace, "features", key);
        Record record = client.get(null, aerospikeKey);
        return record != null ? record.getValue(binName) : null;
    }

    public Map<String, Object> getAllFeatures(String key) {
        Key aerospikeKey = new Key(namespace, "features", key);
        Record record = client.get(null, aerospikeKey);
        if (record == null) return new HashMap<>();
        return record.bins;
    }

    public void incrementCounter(String key, String binName, int value) {
        Key aerospikeKey = new Key(namespace, "counters", key);
        Bin bin = new Bin(binName, value);
        client.add(writePolicy, aerospikeKey, bin);
    }

    public long getCounter(String key, String binName) {
        Key aerospikeKey = new Key(namespace, "counters", key);
        Record record = client.get(null, aerospikeKey);
        return record != null ? record.getLong(binName) : 0L;
    }

    public void storeRiskScore(String entityId, double score, String riskType) {
        Key key = new Key(namespace, "risk_scores", entityId);
        Bin scoreBin = new Bin("score", score);
        Bin typeBin = new Bin("riskType", riskType);
        Bin timestampBin = new Bin("lastUpdated", System.currentTimeMillis());
        client.put(writePolicy, key, scoreBin, typeBin, timestampBin);
    }
}