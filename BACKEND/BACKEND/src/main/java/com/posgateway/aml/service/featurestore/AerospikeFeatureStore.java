package com.posgateway.aml.service.featurestore;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance Feature Store backed by Aerospike.
 * Used for real-time fraud feature lookup (< 5ms target).
 */
@Service
public class AerospikeFeatureStore {

    private final AerospikeClient aerospikeClient;
    private final String namespace = "aml";
    private final String setName = "customer_features";

    private final WritePolicy writePolicy;

    public AerospikeFeatureStore(AerospikeClient aerospikeClient) {
        this.aerospikeClient = aerospikeClient;
        this.writePolicy = new WritePolicy();
        this.writePolicy.expiration = 86400; // 24 hours
    }

    public void saveFeatures(Long customerId, Map<String, Object> features) {
        Key key = new Key(namespace, setName, customerId.toString());
        Bin[] bins = features.entrySet().stream()
                .map(e -> new Bin(e.getKey(), e.getValue()))
                .toArray(Bin[]::new);

        aerospikeClient.put(writePolicy, key, bins);
    }

    public Map<String, Object> getFeatures(Long customerId) {
        Key key = new Key(namespace, setName, customerId.toString());
        Record record = aerospikeClient.get(null, key);

        if (record == null) return new ConcurrentHashMap<>();

        Map<String, Object> features = new ConcurrentHashMap<>();
        record.bins.forEach(features::put);
        return features;
    }
}