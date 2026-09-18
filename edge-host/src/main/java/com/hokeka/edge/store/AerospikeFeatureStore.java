package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.policy.BatchPolicy;
import com.aerospike.client.policy.WritePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Aerospike-backed on-premises store for transactions, velocity counters and the rule bundle.
 *
 * <h2>Data model</h2>
 * <pre>
 *   namespace &lt;configured, default "hokeka"&gt;
 *     set txn       key = txnId                    full transaction, TTL = retention days
 *     set velocity  key = "&lt;panHash&gt;:yyyyMMddHH"   hourly counters {count, amount_cents}
 *     set rules     key = "active"                 {version, ir, saved_at}
 * </pre>
 *
 * <h2>Why hourly buckets</h2>
 * Aerospike is a key-value store: counting a card's recent transactions by scanning is O(n) and
 * needs a secondary index. Instead each transaction atomically increments the counter record for its
 * hour ({@code Operation.add} — a single server-side atomic op, no read-modify-write race), and a
 * window is read as a bounded batch of at most 24 key lookups. Buckets carry a TTL slightly longer
 * than the widest window, so expiry is automatic and no cleanup job is needed.
 *
 * <p>Every method is fail-soft: a store outage degrades enrichment (features are simply absent) but
 * never fails a decision. Fail-closed behaviour remains the engine's job.
 */
public class AerospikeFeatureStore implements EdgeFeatureStore {

    private static final Logger log = LoggerFactory.getLogger(AerospikeFeatureStore.class);

    static final String SET_TXN = "txn";
    static final String SET_VELOCITY = "velocity";
    static final String SET_RULES = "rules";
    static final String RULES_KEY = "active";

    static final String BIN_COUNT = "count";
    static final String BIN_AMOUNT = "amount_cents";
    static final String BIN_VERSION = "version";
    static final String BIN_IR = "ir";

    private static final DateTimeFormatter HOUR =
            DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);

    /** Widest velocity window read back, in hours. */
    private static final int WINDOW_HOURS = 24;

    private final AerospikeClient client;
    private final String namespace;
    private final int txnTtlSeconds;
    private final int velocityTtlSeconds;

    public AerospikeFeatureStore(AerospikeClient client, String namespace, int txnRetentionDays) {
        this.client = client;
        this.namespace = namespace;
        this.txnTtlSeconds = Math.max(1, txnRetentionDays) * 24 * 3600;
        // Keep buckets a little past the widest window so a boundary read never misses a live bucket.
        this.velocityTtlSeconds = (WINDOW_HOURS + 2) * 3600;
    }

    @Override
    public boolean available() {
        try {
            return client.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> deriveFeatures(String panHash) {
        return deriveFeaturesDetailed(panHash).features();
    }

    @Override
    public FeatureDerivation deriveFeaturesDetailed(String panHash) {
        Map<String, Object> features = new LinkedHashMap<>();
        if (isBlank(panHash)) {
            return new FeatureDerivation(features, false);
        }
        if (!available()) {
            return new FeatureDerivation(features, true);
        }
        try {
            Instant now = Instant.now();
            Key[] keys = new Key[WINDOW_HOURS];
            for (int hoursBack = 0; hoursBack < WINDOW_HOURS; hoursBack++) {
                keys[hoursBack] = velocityKey(panHash, now.minusSeconds(hoursBack * 3600L));
            }

            Record[] records = client.get(new BatchPolicy(), keys);
            long count1h = 0;
            long count24h = 0;
            long amount24h = 0;

            for (int hoursBack = 0; hoursBack < WINDOW_HOURS; hoursBack++) {
                Record record = records[hoursBack];
                if (record == null) {
                    continue;
                }
                long c = record.getLong(BIN_COUNT);
                long a = record.getLong(BIN_AMOUNT);
                count24h += c;
                amount24h += a;
                if (hoursBack == 0) {
                    count1h = c;
                }
            }

            features.put("pan_txn_count_1h", count1h);
            features.put("pan_txn_count_24h", count24h);
            // Cents → major units, matching the control plane's enrichment convention.
            features.put("pan_amount_sum_24h", amount24h / 100.0);
            return new FeatureDerivation(features, false);
        } catch (Exception e) {
            log.warn("Velocity enrichment unavailable for this evaluation: {}", e.getMessage());
            return new FeatureDerivation(features, true);
        }
    }

    @Override
    public void recordTransaction(String panHash, Map<String, Object> features,
                                  com.hokeka.edge.EdgeRuleInterpreter.Decision decision) {
        try {
            long amountCents = asLong(features.get("amount_cents"));
            Instant now = Instant.now();

            // 1) Advance the velocity counters for this card — a single atomic server-side op.
            if (!isBlank(panHash)) {
                WritePolicy velocityPolicy = new WritePolicy();
                velocityPolicy.expiration = velocityTtlSeconds;
                client.operate(velocityPolicy, velocityKey(panHash, now),
                        Operation.add(new Bin(BIN_COUNT, 1L)),
                        Operation.add(new Bin(BIN_AMOUNT, amountCents)));
            }

            // 2) Persist the transaction itself so future rule checks have real history to read.
            Object txnId = features.get("txn_id");
            if (txnId != null) {
                WritePolicy txnPolicy = new WritePolicy();
                txnPolicy.expiration = txnTtlSeconds;
                client.put(txnPolicy, new Key(namespace, SET_TXN, String.valueOf(txnId)),
                        new Bin("pan_hash", panHash == null ? "" : panHash),
                        new Bin(BIN_AMOUNT, amountCents),
                        new Bin("ts", now.toEpochMilli()),
                        new Bin("merchant_id", String.valueOf(features.getOrDefault("merchant_id", ""))),
                        new Bin("currency", String.valueOf(features.getOrDefault("currency", ""))),
                        new Bin("country_code", String.valueOf(features.getOrDefault("country_code", ""))),
                        new Bin("mcc", String.valueOf(features.getOrDefault("mcc", ""))),
                        // The decision and its rule attribution are the audit-relevant part: without
                        // them the stored transaction cannot answer "what did we do, and why?".
                        new Bin("decision", decision == null ? "" : decision.action().name()),
                        new Bin("score", decision == null ? 0 : decision.score()),
                        new Bin("triggered_rule_ids", decision == null ? ""
                                : decision.triggeredRuleIds().stream().map(String::valueOf)
                                          .collect(java.util.stream.Collectors.joining(","))),
                        new Bin("reasons", decision == null ? ""
                                : String.join(" | ", decision.reasons())));
            }
        } catch (Exception e) {
            // Never fail the decision because the store is unhappy.
            log.warn("Could not record transaction locally: {}", e.getMessage());
        }
    }

    @Override
    public void saveRuleBundle(long version, byte[] ruleIrJson) {
        if (ruleIrJson == null || ruleIrJson.length == 0) {
            return;
        }
        try {
            WritePolicy policy = new WritePolicy();
            policy.expiration = -1; // never expire: the bundle must survive an offline restart
            client.put(policy, new Key(namespace, SET_RULES, RULES_KEY),
                    new Bin(BIN_VERSION, version),
                    new Bin(BIN_IR, ruleIrJson),
                    new Bin("saved_at", Instant.now().toEpochMilli()));
            log.info("Persisted rule bundle v{} to the local store ({} bytes)", version, ruleIrJson.length);
        } catch (Exception e) {
            log.warn("Could not persist the rule bundle locally: {}", e.getMessage());
        }
    }

    @Override
    public Optional<byte[]> loadRuleBundle() {
        try {
            Record record = client.get(null, new Key(namespace, SET_RULES, RULES_KEY));
            if (record == null) {
                return Optional.empty();
            }
            Object ir = record.getValue(BIN_IR);
            if (ir instanceof byte[] bytes && bytes.length > 0) {
                log.info("Recovered persisted rule bundle v{} from the local store",
                        record.getLong(BIN_VERSION));
                return Optional.of(bytes);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Could not read the persisted rule bundle: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Key velocityKey(String panHash, Instant at) {
        return new Key(namespace, SET_VELOCITY, panHash + ":" + HOUR.format(at));
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
