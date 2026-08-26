package com.hokeka.aml.service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages Aerospike-backed caches for risk profiles, velocity counters,
 * device fingerprints, and IP reputation.
 *
 * Namespace layout per architecture plan:
 *   namespace: aml_cache
 *     set: risk_profile   — CRA scores (TTL: 30 min)
 *     set: velocity       — velocity counters (TTL: 7 days)
 *     set: device         — device reputation (TTL: 1h / 24h)
 *     set: ip_reputation  — IP scores (TTL: 30 min)
 */
@Service
public class AerospikeCacheService {

    private static final Logger log = LoggerFactory.getLogger(AerospikeCacheService.class);

    // W22-2 fix: was a hardcoded static final constant, duplicated (with the same literal value)
    // in AmlCheckService and SanctionsService -- three separate hardcoded copies with no single
    // source of truth, and no way to point at a different keyspace without a rebuild. Externalized
    // via aerospike.namespace, defaulting to the same value so existing deployments are unaffected.
    @Value("${aerospike.namespace:aml_cache}")
    private String namespace;

    // Set names
    private static final String SET_RISK_PROFILE  = "risk_profile";
    private static final String SET_VELOCITY      = "velocity";
    private static final String SET_DEVICE        = "device";
    private static final String SET_IP_REPUTATION = "ip_reputation";

    // TTLs in seconds
    private static final int TTL_RISK_PROFILE  = 1800;  // 30 min
    private static final int TTL_VELOCITY      = 604800; // 7 days
    private static final int TTL_DEVICE_CLEAN  = 3600;  // 1 hour
    private static final int TTL_DEVICE_FLAGGED = 86400; // 24 hours
    private static final int TTL_IP_REPUTATION = 1800;  // 30 min

    @Autowired(required = false)
    private AerospikeClient aerospikeClient;

    public boolean isConnected() {
        return aerospikeClient != null && aerospikeClient.isConnected();
    }

    // ──────────────────────────────────────────────────────────────────
    // Risk Profile
    // ──────────────────────────────────────────────────────────────────

    public void putRiskProfile(Long customerId, Map<String, Object> profile) {
        if (!isConnected() || customerId == null) return;
        try {
            Key key = new Key(namespace, SET_RISK_PROFILE, customerId.toString());
            WritePolicy wp = new WritePolicy();
            wp.expiration = TTL_RISK_PROFILE;
            Bin[] bins = toBins(profile);
            aerospikeClient.put(wp, key, bins);
            log.debug("Risk profile cached via Aerospike for customerId={}", customerId);
        } catch (Exception e) {
            log.warn("Aerospike risk-profile write failed for customerId={}: {}", customerId, e.getMessage());
        }
    }

    public Map<String, Object> getRiskProfile(Long customerId) {
        if (!isConnected() || customerId == null) return null;
        try {
            Key key = new Key(namespace, SET_RISK_PROFILE, customerId.toString());
            Record rec = aerospikeClient.get(null, key);
            if (rec == null) return null;
            return toMap(rec);
        } catch (Exception e) {
            log.warn("Aerospike risk-profile read failed for customerId={}: {}", customerId, e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Velocity
    // ──────────────────────────────────────────────────────────────────

    public void recordVelocity(String customerId, Map<String, Object> event) {
        if (!isConnected() || customerId == null || customerId.isBlank()) return;
        try {
            Key key = new Key(namespace, SET_VELOCITY, customerId);
            WritePolicy wp = new WritePolicy();
            wp.expiration = TTL_VELOCITY;
            // Extract last timestamp safely (avoid ternary type resolution in Bin arg)
            Object rawTs = event != null ? event.getOrDefault("tsMs", System.currentTimeMillis()) : System.currentTimeMillis();
            long lastTs = rawTs instanceof Number n ? n.longValue() : System.currentTimeMillis();
            // Actual transaction amount to accumulate (was hardcoded 0, so the running total
            // never grew and amount-velocity rules always read 0).
            Object rawAmt = event != null
                    ? event.getOrDefault("amountCents", event.getOrDefault("amount", 0L)) : 0L;
            long amount = rawAmt instanceof Number n2 ? n2.longValue() : 0L;
            // Accumulate a TX count, total amount, and last timestamp
            aerospikeClient.operate(wp, key,
                    new com.aerospike.client.Operation[]{
                            com.aerospike.client.Operation.add(new Bin("txn_count", 1L)),
                            // Bin holds the accumulated transaction amount — named clearly so a
                            // future reader can't mistake it for an elapsed-ms value (W49-12).
                            com.aerospike.client.Operation.add(new Bin("total_amount", amount)),
                            com.aerospike.client.Operation.put(new Bin("last_ts_ms", lastTs))
                    });
            log.debug("Velocity event recorded for customerId={}", customerId);
        } catch (Exception e) {
            log.warn("Aerospike velocity write failed for customerId={}: {}", customerId, e.getMessage());
        }
    }

    public Map<String, Object> getVelocity(String customerId) {
        if (!isConnected() || customerId == null || customerId.isBlank()) return null;
        try {
            Key key = new Key(namespace, SET_VELOCITY, customerId);
            Record rec = aerospikeClient.get(null, key);
            if (rec == null) return null;
            return toMap(rec);
        } catch (Exception e) {
            log.warn("Aerospike velocity read failed for customerId={}: {}", customerId, e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Device fingerprint
    // ──────────────────────────────────────────────────────────────────

    public void putDevice(String fingerprint, Map<String, Object> obs) {
        if (!isConnected() || fingerprint == null || fingerprint.isBlank()) return;
        try {
            Key key = new Key(namespace, SET_DEVICE, fingerprint);
            WritePolicy wp = new WritePolicy();
            wp.expiration = isFlaggedDevice(obs) ? TTL_DEVICE_FLAGGED : TTL_DEVICE_CLEAN;
            aerospikeClient.put(wp, key, toBins(obs));
            log.debug("Device observation cached via Aerospike fp={}", fingerprint);
        } catch (Exception e) {
            log.warn("Aerospike device write failed for fp={}: {}", fingerprint, e.getMessage());
        }
    }

    public Map<String, Object> getDevice(String fingerprint) {
        if (!isConnected() || fingerprint == null || fingerprint.isBlank()) return null;
        try {
            Key key = new Key(namespace, SET_DEVICE, fingerprint);
            Record rec = aerospikeClient.get(null, key);
            return rec != null ? toMap(rec) : null;
        } catch (Exception e) {
            log.warn("Aerospike device read failed for fp={}: {}", fingerprint, e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // IP reputation
    // ──────────────────────────────────────────────────────────────────

    public void putIpReputation(String ip, Map<String, Object> obs) {
        if (!isConnected() || ip == null || ip.isBlank()) return;
        try {
            Key key = new Key(namespace, SET_IP_REPUTATION, ip);
            WritePolicy wp = new WritePolicy();
            wp.expiration = TTL_IP_REPUTATION;
            aerospikeClient.put(wp, key, toBins(obs));
            log.debug("IP reputation cached via Aerospike ip={}", ip);
        } catch (Exception e) {
            log.warn("Aerospike ip-reputation write failed for ip={}: {}", ip, e.getMessage());
        }
    }

    public Map<String, Object> getIpReputation(String ip) {
        if (!isConnected() || ip == null || ip.isBlank()) return null;
        try {
            Key key = new Key(namespace, SET_IP_REPUTATION, ip);
            Record rec = aerospikeClient.get(null, key);
            return rec != null ? toMap(rec) : null;
        } catch (Exception e) {
            log.warn("Aerospike ip-reputation read failed for ip={}: {}", ip, e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    private static Bin[] toBins(Map<String, Object> map) {
        if (map == null) return new Bin[0];
        return map.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .map(e -> {
                    Object v = e.getValue();
                    if (v instanceof String s)    return new Bin(e.getKey(), s);
                    if (v instanceof Long l)      return new Bin(e.getKey(), l);
                    if (v instanceof Integer i)    return new Bin(e.getKey(), i.longValue());
                    if (v instanceof Double d)    return new Bin(e.getKey(), d);
                    if (v instanceof Float f)     return new Bin(e.getKey(), f.doubleValue());
                    if (v instanceof Boolean b)   return new Bin(e.getKey(), b);
                    if (v instanceof List<?> li)  return new Bin(e.getKey(), li);
                    if (v instanceof Map<?,?> m2) return new Bin(e.getKey(), m2);
                    return new Bin(e.getKey(), v != null ? v.toString() : "");
                })
                .toArray(Bin[]::new);
    }

    private static Map<String, Object> toMap(Record rec) {
        Map<String, Object> m = new HashMap<>();
        rec.bins.forEach((k, v) -> {
            Object val = v;
            // Aerospike returns byte[] for keys it can't natively serialize;
            // convert back as long/double where possible
            if (val instanceof Long l) m.put(k, l);
            else if (val instanceof Double d) m.put(k, d);
            else if (val instanceof String s) m.put(k, s);
            else if (val instanceof byte[]) m.put(k, val);
            else m.put(k, String.valueOf(val));
        });
        return m;
    }

    private static boolean isFlaggedDevice(Map<String, Object> obs) {
        if (obs == null) return false;
        Object flagged = obs.getOrDefault("flagged", obs.getOrDefault("isFlagged", false));
        if (flagged instanceof Boolean b) return b;
        if (flagged instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }
}