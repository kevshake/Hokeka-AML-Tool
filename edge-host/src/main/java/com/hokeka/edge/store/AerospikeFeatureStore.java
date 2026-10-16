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
 * Aerospike-backed on-premises store for velocity counters and the rule bundle.
 *
 * <h2>Data model</h2>
 * <pre>
 *   namespace &lt;configured, default "hokeka"&gt;
 *     set velocity  key = "&lt;panHash&gt;:yyyyMMddHH"   hourly counters {count, amount_cents}, TTL 26 h
 *     set rules     key = "active"                 {version, ir, saved_at}, never expires
 * </pre>
 *
 * <p>Transactions are <b>not</b> stored here any more: the system of record for evaluations is the
 * Edge Ledger (PostgreSQL, {@code com.hokeka.edge.ledger}), which holds them for the PSP's
 * multi-year retention with range queries and partition purges — neither of which an in-RAM primary
 * index can afford (EDGE_DATA_RESIDENCY_PLAN.md, "Edge storage"). The former {@code txn} set is gone.
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
    private final int velocityTtlSeconds;

    /**
     * @param client    connected Aerospike client
     * @param namespace namespace holding the velocity and rules sets
     */
    public AerospikeFeatureStore(AerospikeClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;
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

    /**
     * Advances this card's hourly velocity bucket: count + 1 and amount sum + {@code amountCents},
     * as one atomic server-side {@code operate}. A blank pan hash records nothing. Never throws.
     */
    @Override
    public void recordVelocity(String panHash, long amountCents) {
        if (isBlank(panHash)) {
            return;
        }
        try {
            WritePolicy velocityPolicy = new WritePolicy();
            velocityPolicy.expiration = velocityTtlSeconds;
            client.operate(velocityPolicy, velocityKey(panHash, Instant.now()),
                    Operation.add(new Bin(BIN_COUNT, 1L)),
                    Operation.add(new Bin(BIN_AMOUNT, Math.max(0L, amountCents))));
        } catch (Exception e) {
            // Never fail the decision because the store is unhappy.
            log.warn("Could not advance velocity counters locally: {}", e.getMessage());
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
