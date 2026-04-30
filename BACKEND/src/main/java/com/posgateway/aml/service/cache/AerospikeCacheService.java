package com.posgateway.aml.service.cache;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.posgateway.aml.service.AerospikeConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Aerospike Cache Service
 * Provides high-performance caching for all AML features
 */
@Service
public class AerospikeCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeCacheService.class);

    @Autowired
    private AerospikeConnectionService aerospikeConnectionService;

    @Value("${aerospike.namespace:test}")
    private String namespace;

    @Value("${aerospike.cache.default.ttl.seconds:3600}")
    private int defaultTtlSeconds;

    /**
     * Put a value in cache with default TTL
     */
    public void put(String set, String key, Object value) {
        put(set, key, value, defaultTtlSeconds);
    }

    /**
     * Put a value in cache with custom TTL
     */
    public void put(String set, String key, Object value, int ttlSeconds) {
        if (!aerospikeConnectionService.isConnected()) {
            logger.debug("Aerospike not connected, skipping cache put for {}/{}", set, key);
            return;
        }

        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Key recordKey = new Key(namespace, set, key);
            
            WritePolicy writePolicy = new WritePolicy();
            writePolicy.expiration = ttlSeconds;

            Bin bin;
            if (value instanceof String) {
                bin = new Bin("value", (String) value);
            } else if (value instanceof Number) {
                bin = new Bin("value", ((Number) value).longValue());
            } else if (value instanceof Boolean) {
                bin = new Bin("value", (Boolean) value ? 1L : 0L);
            } else {
                // For complex objects, serialize to JSON string
                bin = new Bin("value", value.toString());
            }
            client.put(writePolicy, recordKey, bin);
            
            logger.debug("Cached value in Aerospike: {}/{} (TTL: {}s)", set, key, ttlSeconds);
        } catch (Exception e) {
            logger.warn("Failed to cache value in Aerospike: {}/{} - {}", set, key, e.getMessage());
        }
    }

    /**
     * Put multiple values in cache
     */
    public void putAll(String set, Map<String, Object> values, int ttlSeconds) {
        if (!aerospikeConnectionService.isConnected()) {
            logger.debug("Aerospike not connected, skipping cache putAll for {}", set);
            return;
        }

        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            WritePolicy writePolicy = new WritePolicy();
            writePolicy.expiration = ttlSeconds;

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Key recordKey = new Key(namespace, set, entry.getKey());
                Bin bin;
                Object val = entry.getValue();
                if (val instanceof String) {
                    bin = new Bin("value", (String) val);
                } else if (val instanceof Number) {
                    bin = new Bin("value", ((Number) val).longValue());
                } else if (val instanceof Boolean) {
                    bin = new Bin("value", (Boolean) val ? 1L : 0L);
                } else {
                    bin = new Bin("value", val.toString());
                }
                client.put(writePolicy, recordKey, bin);
            }
            
            logger.debug("Cached {} values in Aerospike set: {}", values.size(), set);
        } catch (Exception e) {
            logger.warn("Failed to cache values in Aerospike set: {} - {}", set, e.getMessage());
        }
    }

    /**
     * Get a value from cache
     */
    public Object get(String set, String key) {
        if (!aerospikeConnectionService.isConnected()) {
            logger.debug("Aerospike not connected, skipping cache get for {}/{}", set, key);
            return null;
        }

        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Key recordKey = new Key(namespace, set, key);
            
            Record record = client.get(null, recordKey);
            
            if (record != null) {
                logger.debug("Cache hit in Aerospike: {}/{}", set, key);
                return record.getValue("value");
            }
            
            logger.debug("Cache miss in Aerospike: {}/{}", set, key);
            return null;
        } catch (Exception e) {
            logger.warn("Failed to get value from Aerospike cache: {}/{} - {}", set, key, e.getMessage());
            return null;
        }
    }

    /**
     * Get a value with type casting
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String set, String key, Class<T> type) {
        Object value = get(set, key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Check if a key exists in cache
     */
    public boolean exists(String set, String key) {
        if (!aerospikeConnectionService.isConnected()) {
            return false;
        }

        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Key recordKey = new Key(namespace, set, key);
            return client.exists(null, recordKey);
        } catch (Exception e) {
            logger.warn("Failed to check existence in Aerospike: {}/{} - {}", set, key, e.getMessage());
            return false;
        }
    }

    /**
     * Delete a value from cache
     */
    public void delete(String set, String key) {
        if (!aerospikeConnectionService.isConnected()) {
            return;
        }

        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Key recordKey = new Key(namespace, set, key);
            client.delete(null, recordKey);
            logger.debug("Deleted from Aerospike cache: {}/{}", set, key);
        } catch (Exception e) {
            logger.warn("Failed to delete from Aerospike cache: {}/{} - {}", set, key, e.getMessage());
        }
    }

    /**
     * Put a map of values
     */
    public void putMap(String set, String key, Map<String, Object> map, int ttlSeconds) {
        if (!aerospikeConnectionService.isConnected()) {
            return;
        }

        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Key recordKey = new Key(namespace, set, key);
            
            WritePolicy writePolicy = new WritePolicy();
            writePolicy.expiration = ttlSeconds;

            Bin[] bins = new Bin[map.size()];
            int i = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object val = entry.getValue();
                Bin bin;
                if (val instanceof String) {
                    bin = new Bin(entry.getKey(), (String) val);
                } else if (val instanceof Number) {
                    bin = new Bin(entry.getKey(), ((Number) val).longValue());
                } else if (val instanceof Boolean) {
                    bin = new Bin(entry.getKey(), (Boolean) val ? 1L : 0L);
                } else {
                    bin = new Bin(entry.getKey(), val.toString());
                }
                bins[i++] = bin;
            }
            
            client.put(writePolicy, recordKey, bins);
            logger.debug("Cached map in Aerospike: {}/{} ({} entries)", set, key, map.size());
        } catch (Exception e) {
            logger.warn("Failed to cache map in Aerospike: {}/{} - {}", set, key, e.getMessage());
        }
    }

    /**
     * Get a map of values
     */
    public Map<String, Object> getMap(String set, String key) {
        if (!aerospikeConnectionService.isConnected()) {
            return null;
        }

        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Key recordKey = new Key(namespace, set, key);
            
            Record record = client.get(null, recordKey);
            if (record != null) {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, Object> entry : record.bins.entrySet()) {
                    map.put(entry.getKey(), entry.getValue());
                }
                return map;
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to get map from Aerospike: {}/{} - {}", set, key, e.getMessage());
            return null;
        }
    }
}

