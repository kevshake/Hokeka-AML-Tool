package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.ResultCode;
import com.aerospike.client.policy.BatchPolicy;
import com.aerospike.client.policy.GenerationPolicy;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aerospike-backed on-premises store for card history, compliance counters and the rule bundle.
 *
 * <h2>Data model (namespace configured, default "hokeka"; every key ends with the site id)</h2>
 * <pre>
 *   set velocity        "&lt;pan&gt;:yyyyMMddHH:&lt;site&gt;"  hourly {count, amount_cents, amount_n, in_count,
 *                                                   out_count, in_cents, out_cents, band}, TTL 50 h
 *   set velocity_day    "&lt;pan&gt;:yyyyMMdd:&lt;site&gt;"    daily {count, cents, amount_n, sq, in_count, out_count},
 *                                                   TTL 32 d
 *   set card_profile    "&lt;pan&gt;:&lt;site&gt;"             {first, last, recent} — recent = last 100 evaluations, TTL 800 d
 *   set device_profile  "&lt;device&gt;:&lt;site&gt;"          {recent}, TTL 400 d
 *   set ip_profile      "&lt;ip&gt;:&lt;site&gt;"              {recent}, TTL 400 d
 *   set &lt;counter set&gt;   "&lt;key&gt;:&lt;site&gt;"             compliance counters (CTR card-day, limits)
 *   set rules           "active"                        {version, ir, saved_at}, never expires
 * </pre>
 *
 * <h2>Site-scoped</h2>
 * All nodes of one Edge Site share one Aerospike cluster, so they advance and read the same
 * records; reads use the local site's keys only. Across sites, history is per site unless the
 * clusters are linked by XDR (EDGE_DATA_RESIDENCY_PLAN.md, conflict 3).
 *
 * <h2>Cost per evaluation (bounded, no scans)</h2>
 * One batch get of at most 48 + 31 + 3 = 82 keys before evaluation; after it, two atomic
 * {@code operate} adds (hour, day) and up to three profile read-modify-writes (card, device, IP),
 * each a generation-checked {@code put} of a record holding at most 100 entries. Counters for CTR
 * and limits are separate small batches issued by the compliance overlay only when the Edge
 * Config enables them.
 *
 * <p>Every method is fail-soft: a store outage makes history unavailable (reported, so the
 * controller can fail closed) but never throws.
 */
public class AerospikeFeatureStore implements EdgeFeatureStore {

    private static final Logger log = LoggerFactory.getLogger(AerospikeFeatureStore.class);

    static final String SET_VELOCITY = "velocity";
    static final String SET_DAY = "velocity_day";
    static final String SET_CARD = "card_profile";
    static final String SET_DEVICE = "device_profile";
    static final String SET_IP = "ip_profile";
    static final String SET_RULES = "rules";
    static final String RULES_KEY = "active";

    static final String BIN_COUNT = "count";
    static final String BIN_AMOUNT = "amount_cents";
    static final String BIN_AMOUNT_N = "amount_n";
    static final String BIN_IN_COUNT = "in_count";
    static final String BIN_OUT_COUNT = "out_count";
    static final String BIN_IN_CENTS = "in_cents";
    static final String BIN_OUT_CENTS = "out_cents";
    static final String BIN_BAND = "band";
    static final String BIN_CENTS = "cents";
    static final String BIN_SQUARES = "sq";
    static final String BIN_FIRST = "first";
    static final String BIN_LAST = "last";
    static final String BIN_RECENT = "recent";
    static final String BIN_VERSION = "version";
    static final String BIN_IR = "ir";

    private static final DateTimeFormatter HOUR =
            DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final int HOUR_TTL = (CardHistory.HOURS + 2) * 3600;
    private static final int DAY_TTL = (CardHistory.DAYS + 1) * 86400;
    private static final int CARD_TTL = 800 * 86400;
    private static final int LINK_TTL = 400 * 86400;
    /** Attempts of a profile read-modify-write before giving up on a contended record. */
    private static final int PROFILE_ATTEMPTS = 4;

    private final AerospikeClient client;
    private final String namespace;
    private final String siteId;

    /**
     * Creates the store for one Edge Site.
     *
     * @param client    connected Aerospike client
     * @param namespace namespace holding the sets
     * @param siteId    this node's Edge Site id; the last segment of every key
     * @throws IllegalArgumentException when {@code siteId} is null or blank
     */
    public AerospikeFeatureStore(AerospikeClient client, String namespace, String siteId) {
        if (isBlank(siteId)) {
            throw new IllegalArgumentException("siteId is required: velocity keys are site-scoped");
        }
        this.client = client;
        this.namespace = namespace;
        this.siteId = siteId.trim();
    }

    /** The Edge Site id the keys are scoped to. */
    public String siteId() {
        return siteId;
    }

    /** True while the Aerospike client reports a live cluster connection. Never throws. */
    @Override
    public boolean available() {
        try {
            return client.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /** History features for a card alone (no device / IP), see {@link #derive}. */
    @Override
    public Map<String, Object> deriveFeatures(String panHash) {
        return deriveFeaturesDetailed(panHash).features();
    }

    /** {@link #derive} for a card alone, evaluated now. */
    @Override
    public FeatureDerivation deriveFeaturesDetailed(String panHash) {
        if (isBlank(panHash)) {
            return new FeatureDerivation(Map.of(), false);
        }
        return derive(new TxnContext(Instant.now(), panHash, null, null, null, null, null,
                TxnContext.OUTBOUND, false, null, null, null));
    }

    /**
     * Reads the card's 48 hourly and 31 daily buckets, its profile and the device / IP profiles of
     * the local site in one batch get.
     */
    @Override
    public Optional<CardHistory> readHistory(TxnContext txn) {
        boolean card = txn.hasCard();
        if (!card && txn.deviceKey() == null && txn.ipAddress() == null) {
            return Optional.of(CardHistory.empty());
        }
        if (!available()) {
            return Optional.empty();
        }
        try {
            List<Key> keys = new ArrayList<>();
            Instant now = txn.at();
            if (card) {
                for (int h = 0; h < CardHistory.HOURS; h++) {
                    keys.add(hourKey(txn.panHash(), now.minusSeconds(h * 3600L)));
                }
                for (int d = 0; d < CardHistory.DAYS; d++) {
                    keys.add(dayKey(txn.panHash(), now.minusSeconds(d * 86400L)));
                }
                keys.add(profileKey(SET_CARD, txn.panHash()));
            }
            int deviceIndex = -1;
            int ipIndex = -1;
            if (txn.deviceKey() != null) {
                deviceIndex = keys.size();
                keys.add(profileKey(SET_DEVICE, txn.deviceKey()));
            }
            if (txn.ipAddress() != null) {
                ipIndex = keys.size();
                keys.add(profileKey(SET_IP, txn.ipAddress()));
            }
            Record[] r = client.get(new BatchPolicy(), keys.toArray(new Key[0]));

            CardHistory empty = CardHistory.empty();
            List<CardHistory.Hour> hours = new ArrayList<>(empty.hours());
            List<CardHistory.Day> days = new ArrayList<>(empty.days());
            long first = 0;
            long last = 0;
            List<CardHistory.Entry> recent = List.of();
            if (card) {
                for (int h = 0; h < CardHistory.HOURS; h++) {
                    Record b = r[h];
                    if (b != null) {
                        hours.set(h, new CardHistory.Hour(b.getLong(BIN_COUNT), b.getLong(BIN_AMOUNT),
                                b.getLong(BIN_AMOUNT_N), b.getLong(BIN_IN_COUNT), b.getLong(BIN_OUT_COUNT),
                                b.getLong(BIN_IN_CENTS), b.getLong(BIN_OUT_CENTS), b.getLong(BIN_BAND)));
                    }
                }
                for (int d = 0; d < CardHistory.DAYS; d++) {
                    Record b = r[CardHistory.HOURS + d];
                    if (b != null) {
                        days.set(d, new CardHistory.Day(b.getLong(BIN_COUNT), b.getLong(BIN_CENTS),
                                b.getLong(BIN_AMOUNT_N), b.getDouble(BIN_SQUARES), b.getLong(BIN_IN_COUNT),
                                b.getLong(BIN_OUT_COUNT)));
                    }
                }
                Record p = r[CardHistory.HOURS + CardHistory.DAYS];
                if (p != null) {
                    first = p.getLong(BIN_FIRST);
                    last = p.getLong(BIN_LAST);
                    recent = CardHistory.decode(p.getString(BIN_RECENT));
                }
            }
            List<CardHistory.Entry> deviceRecent = deviceIndex >= 0 && r[deviceIndex] != null
                    ? CardHistory.decode(r[deviceIndex].getString(BIN_RECENT)) : List.of();
            List<CardHistory.Entry> ipRecent = ipIndex >= 0 && r[ipIndex] != null
                    ? CardHistory.decode(r[ipIndex].getString(BIN_RECENT)) : List.of();
            return Optional.of(new CardHistory(hours, days, first, last, recent, ipRecent, deviceRecent));
        } catch (Exception e) {
            log.warn("History read unavailable for this evaluation: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Records a card-only transaction of {@code amountCents} now (legacy entry point). */
    @Override
    public void recordVelocity(String panHash, long amountCents) {
        if (isBlank(panHash)) {
            return;
        }
        record(new TxnContext(Instant.now(), panHash, Math.max(0L, amountCents), null, null, null, null,
                TxnContext.OUTBOUND, false, null, null, null), false);
    }

    /**
     * Advances the card's hourly and daily buckets atomically and appends the evaluation to the
     * card, device and IP profiles. Never throws.
     */
    @Override
    public void record(TxnContext txn, boolean structuringBand) {
        CardHistory.Entry entry = CardHistory.Entry.of(txn);
        long cents = txn.amountCentsOrZero();
        long hasAmount = txn.amountCents() == null ? 0L : 1L;
        boolean in = txn.inbound();
        try {
            if (txn.hasCard()) {
                client.operate(ttl(HOUR_TTL), hourKey(txn.panHash(), txn.at()),
                        Operation.add(new Bin(BIN_COUNT, 1L)),
                        Operation.add(new Bin(BIN_AMOUNT, cents)),
                        Operation.add(new Bin(BIN_AMOUNT_N, hasAmount)),
                        Operation.add(new Bin(BIN_IN_COUNT, in ? 1L : 0L)),
                        Operation.add(new Bin(BIN_OUT_COUNT, in ? 0L : 1L)),
                        Operation.add(new Bin(BIN_IN_CENTS, in ? cents : 0L)),
                        Operation.add(new Bin(BIN_OUT_CENTS, in ? 0L : cents)),
                        Operation.add(new Bin(BIN_BAND, structuringBand ? 1L : 0L)));
                client.operate(ttl(DAY_TTL), dayKey(txn.panHash(), txn.at()),
                        Operation.add(new Bin(BIN_COUNT, 1L)),
                        Operation.add(new Bin(BIN_CENTS, cents)),
                        Operation.add(new Bin(BIN_AMOUNT_N, hasAmount)),
                        Operation.add(new Bin(BIN_SQUARES, hasAmount == 1L ? (double) cents * (double) cents : 0.0)),
                        Operation.add(new Bin(BIN_IN_COUNT, in ? 1L : 0L)),
                        Operation.add(new Bin(BIN_OUT_COUNT, in ? 0L : 1L)));
                appendProfile(SET_CARD, txn.panHash(), entry, CARD_TTL, true);
            }
            if (txn.deviceKey() != null) {
                appendProfile(SET_DEVICE, txn.deviceKey(), entry, LINK_TTL, false);
            }
            if (txn.ipAddress() != null) {
                appendProfile(SET_IP, txn.ipAddress(), entry, LINK_TTL, false);
            }
        } catch (Exception e) {
            // Never fail the decision because the store is unhappy.
            log.warn("Could not advance card history locally: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Prepends an entry to a profile's recent list with a generation-checked write, retrying when
     * another node of the site updated the same record in between.
     */
    private void appendProfile(String set, String id, CardHistory.Entry entry, int ttlSeconds, boolean card) {
        Key key = profileKey(set, id);
        for (int attempt = 0; attempt < PROFILE_ATTEMPTS; attempt++) {
            Record current = client.get(null, key);
            List<CardHistory.Entry> recent = current == null ? List.of()
                    : CardHistory.decode(current.getString(BIN_RECENT));
            WritePolicy policy = ttl(ttlSeconds);
            if (current == null) {
                policy.recordExistsAction = RecordExistsAction.CREATE_ONLY;
            } else {
                policy.generationPolicy = GenerationPolicy.EXPECT_GEN_EQUAL;
                policy.generation = current.generation;
            }
            List<Bin> bins = new ArrayList<>();
            bins.add(new Bin(BIN_RECENT, CardHistory.encode(CardHistory.prepend(entry, recent))));
            if (card) {
                long first = current == null || current.getLong(BIN_FIRST) == 0 ? entry.atMs()
                        : Math.min(current.getLong(BIN_FIRST), entry.atMs());
                long last = current == null ? entry.atMs() : Math.max(current.getLong(BIN_LAST), entry.atMs());
                bins.add(new Bin(BIN_FIRST, first));
                bins.add(new Bin(BIN_LAST, last));
            }
            try {
                client.put(policy, key, bins.toArray(new Bin[0]));
                return;
            } catch (AerospikeException e) {
                if (e.getResultCode() != ResultCode.GENERATION_ERROR && e.getResultCode() != ResultCode.KEY_EXISTS_ERROR) {
                    throw e;
                }
            }
        }
        log.warn("Profile update in set {} lost {} races in a row; this evaluation is not in its history",
                set, PROFILE_ATTEMPTS);
    }

    /** Reads counter bins of many records in one batch; empty when the store is unavailable. */
    @Override
    public Optional<long[][]> readCounters(List<CounterKey> keys, List<String> bins) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            Key[] k = keys.stream().map(this::counterKey).toArray(Key[]::new);
            Record[] records = k.length == 0 ? new Record[0] : client.get(new BatchPolicy(), k);
            long[][] out = new long[keys.size()][bins.size()];
            for (int i = 0; i < records.length; i++) {
                if (records[i] != null) {
                    for (int j = 0; j < bins.size(); j++) {
                        out[i][j] = records[i].getLong(bins.get(j));
                    }
                }
            }
            return Optional.of(out);
        } catch (Exception e) {
            log.warn("Counter read unavailable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Adds to counter bins and returns the post-add values atomically (one {@code operate}). */
    @Override
    public Optional<long[]> addCounters(CounterKey key, int ttlSeconds, Map<String, Long> increments) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            List<Operation> ops = new ArrayList<>();
            for (Map.Entry<String, Long> e : increments.entrySet()) {
                ops.add(Operation.add(new Bin(e.getKey(), e.getValue())));
            }
            for (String bin : increments.keySet()) {
                ops.add(Operation.get(bin));
            }
            Record r = client.operate(ttl(ttlSeconds), counterKey(key), ops.toArray(new Operation[0]));
            long[] out = new long[increments.size()];
            int i = 0;
            for (String bin : increments.keySet()) {
                out[i++] = r == null ? 0L : r.getLong(bin);
            }
            return Optional.of(out);
        } catch (Exception e) {
            log.warn("Counter update unavailable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Persists the verified rule IR under {@code rules/active} with no expiry. Never throws. */
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

    /** The persisted rule IR, or empty when none is stored or the store cannot be read. */
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

    /** A write policy with the given TTL. */
    private static WritePolicy ttl(int seconds) {
        WritePolicy p = new WritePolicy();
        p.expiration = seconds;
        return p;
    }

    /** Hourly bucket key. */
    private Key hourKey(String panHash, Instant at) {
        return new Key(namespace, SET_VELOCITY, velocityUserKey(panHash, at, siteId));
    }

    /** Daily bucket key {@code <pan>:<yyyyMMdd UTC>:<site>}. */
    private Key dayKey(String panHash, Instant at) {
        return new Key(namespace, SET_DAY, panHash + ":" + DAY.format(at) + ":" + siteId);
    }

    /** Profile key {@code <id>:<site>}. */
    private Key profileKey(String set, String id) {
        return new Key(namespace, set, id + ":" + siteId);
    }

    /** Counter key {@code <key>:<site>}. */
    private Key counterKey(CounterKey key) {
        return new Key(namespace, key.set(), key.key() + ":" + siteId);
    }

    /** The user key {@code <panHash>:<yyyyMMddHH UTC>:<siteId>} (contract format). */
    static String velocityUserKey(String panHash, Instant at, String siteId) {
        return panHash + ":" + HOUR.format(at) + ":" + siteId;
    }

    /** True for null, empty or whitespace-only strings. */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
