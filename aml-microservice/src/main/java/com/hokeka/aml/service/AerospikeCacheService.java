package com.hokeka.aml.service;

import com.aeroorm.AeroRepository;
import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.hokeka.aml.cache.RiskProfileCache;
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
 *     set: velocity       — fixed-window velocity buckets (TTL: 2 x aml.velocity.window-seconds)
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
    // Velocity buckets carry their own TTL (2 x window) — see velocityBucketTtlSeconds().
    private static final int TTL_DEVICE_CLEAN  = 3600;  // 1 hour
    private static final int TTL_DEVICE_FLAGGED = 86400; // 24 hours
    private static final int TTL_IP_REPUTATION = 1800;  // 30 min

    /**
     * Velocity window length (W49-13). Counters are bucketed per window instead of living in one
     * record whose TTL is refreshed on every write.
     */
    @Value("${aml.velocity.window-seconds:3600}")
    private long velocityWindowSeconds = 3600L;

    /** Time source for velocity bucketing; replaceable in tests. */
    private java.time.Clock clock = java.time.Clock.systemUTC();

    @Autowired(required = false)
    private AerospikeClient aerospikeClient;

    @Autowired(required = false)
    private AeroRepository<RiskProfileCache> riskProfileRepository;

    public boolean isConnected() {
        return aerospikeClient != null && aerospikeClient.isConnected();
    }

    // ──────────────────────────────────────────────────────────────────
    // Risk Profile
    // ──────────────────────────────────────────────────────────────────

    public void putRiskProfile(Long customerId, Map<String, Object> profile) {
        if (!isConnected() || customerId == null || riskProfileRepository == null) return;
        try {
            riskProfileRepository.saveMap(customerId.toString(), profile, TTL_RISK_PROFILE);
            log.debug("Risk profile cached via AeroORM for customerId={}", customerId);
        } catch (Exception e) {
            log.warn("AeroORM risk-profile write failed for customerId={}: {}", customerId, e.getMessage());
        }
    }

    public Map<String, Object> getRiskProfile(Long customerId) {
        if (!isConnected() || customerId == null || riskProfileRepository == null) return null;
        try {
            return riskProfileRepository.findMap(customerId.toString());
        } catch (Exception e) {
            log.warn("AeroORM risk-profile read failed for customerId={}: {}", customerId, e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Velocity
    // ──────────────────────────────────────────────────────────────────

    /**
     * Records one transaction into the customer's velocity counter using fixed-window buckets
     * (W49-13 fix). Previously a single record per customer was incremented with
     * {@code expiration = 7 days} on every write, so the TTL slid forward on each transaction and
     * an active customer's counters never reset — the "velocity" was an unbounded lifetime total.
     *
     * <p>Now each event lands in the bucket {@code customerId|floor(tsMs / windowMs)}. A bucket is
     * only ever written during its own window, so refreshing its TTL on write cannot keep it alive
     * past {@code windowEnd + bucketTtl}; it expires on its own after the window closes. The TTL is
     * two windows so {@link #getVelocity} can still read the previous bucket for the sliding
     * estimate.
     */
    public void recordVelocity(String customerId, Map<String, Object> event) {
        if (!isConnected() || customerId == null || customerId.isBlank()) return;
        try {
            // Event timestamp decides the bucket; fall back to "now" when absent or malformed.
            long nowMs = clock.millis();
            Object rawTs = event != null ? event.getOrDefault("tsMs", nowMs) : nowMs;
            long tsMs = rawTs instanceof Number n ? n.longValue() : nowMs;
            // Actual transaction amount to accumulate (minor units when the caller sends amountCents).
            Object rawAmt = event != null
                    ? event.getOrDefault("amountCents", event.getOrDefault("amount", 0L)) : 0L;
            long amount = rawAmt instanceof Number n2 ? n2.longValue() : 0L;

            long windowMs = velocityWindowMs();
            long bucket = Math.floorDiv(tsMs, windowMs);
            Key key = velocityKey(customerId, bucket);
            WritePolicy wp = new WritePolicy();
            wp.expiration = velocityBucketTtlSeconds();
            aerospikeClient.operate(wp, key,
                    new com.aerospike.client.Operation[]{
                            com.aerospike.client.Operation.add(new Bin("txn_count", 1L)),
                            // Accumulated transaction amount (named so it can't be mistaken for ms; W49-12).
                            com.aerospike.client.Operation.add(new Bin("total_amount", amount)),
                            com.aerospike.client.Operation.put(new Bin("last_ts_ms", tsMs)),
                            com.aerospike.client.Operation.put(new Bin("window_start_ms", bucket * windowMs))
                    });
            log.debug("Velocity event recorded for customerId={} bucket={}", customerId, bucket);
        } catch (Exception e) {
            log.warn("Aerospike velocity write failed for customerId={}: {}", customerId, e.getMessage());
        }
    }

    /**
     * Reads the customer's velocity for the configured window.
     *
     * <p>Returns {@code txn_count} / {@code total_amount} for the current fixed window, plus a
     * sliding-window estimate ({@code sliding_txn_count}, {@code sliding_total_amount}) that
     * weights the previous bucket by the fraction of it still inside the trailing window — the
     * standard sliding-window-counter approximation. Returns {@code null} (HTTP 404 upstream)
     * when neither bucket exists, i.e. no activity in the last two windows.
     */
    public Map<String, Object> getVelocity(String customerId) {
        if (!isConnected() || customerId == null || customerId.isBlank()) return null;
        try {
            long windowMs = velocityWindowMs();
            long nowMs = clock.millis();
            long bucket = Math.floorDiv(nowMs, windowMs);
            Record current = aerospikeClient.get(null, velocityKey(customerId, bucket));
            Record previous = aerospikeClient.get(null, velocityKey(customerId, bucket - 1));
            if (current == null && previous == null) return null;

            long curCount = binLong(current, "txn_count");
            long curAmount = binLong(current, "total_amount");
            long prevCount = binLong(previous, "txn_count");
            long prevAmount = binLong(previous, "total_amount");
            // Portion of the previous bucket that still overlaps the trailing window [now-window, now].
            double elapsed = (double) (nowMs - bucket * windowMs) / windowMs;
            double prevWeight = Math.max(0.0, 1.0 - elapsed);

            Map<String, Object> m = new HashMap<>();
            m.put("txn_count", curCount);
            m.put("total_amount", curAmount);
            m.put("window_ms", windowMs);
            m.put("window_start_ms", bucket * windowMs);
            m.put("sliding_txn_count", Math.round(curCount + prevCount * prevWeight));
            m.put("sliding_total_amount", Math.round(curAmount + prevAmount * prevWeight));
            long lastTs = Math.max(binLong(current, "last_ts_ms"), binLong(previous, "last_ts_ms"));
            if (lastTs > 0) m.put("last_ts_ms", lastTs);
            return m;
        } catch (Exception e) {
            log.warn("Aerospike velocity read failed for customerId={}: {}", customerId, e.getMessage());
            return null;
        }
    }

    /** Aerospike key of one velocity bucket: {@code <customerId>|<bucketIndex>}. */
    private Key velocityKey(String customerId, long bucket) {
        return new Key(namespace, SET_VELOCITY, customerId + "|" + bucket);
    }

    /** Configured velocity window in ms; a non-positive config falls back to one hour. */
    private long velocityWindowMs() {
        long seconds = velocityWindowSeconds > 0 ? velocityWindowSeconds : 3600L;
        return seconds * 1000L;
    }

    /** Bucket TTL (seconds): two windows, so the previous bucket stays readable for the sliding estimate. */
    private int velocityBucketTtlSeconds() {
        long seconds = velocityWindowMs() / 1000L * 2L;
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    /** Reads a numeric bin as long; missing record/bin or non-numeric value counts as 0. */
    private static long binLong(Record rec, String bin) {
        if (rec == null || rec.bins == null) return 0L;
        Object v = rec.bins.get(bin);
        return v instanceof Number n ? n.longValue() : 0L;
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