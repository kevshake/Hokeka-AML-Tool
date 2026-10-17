package com.hokeka.edge.typology;

import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.FxConversion;
import com.hokeka.edge.store.CardHistoryFeatures;
import com.hokeka.edge.store.EdgeFeatureStore;
import com.hokeka.edge.store.EdgeFeatureStore.CounterKey;
import com.hokeka.edge.store.TxnContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Edge's built-in compliance checks around rule evaluation, driven by the sealed Edge Config
 * (EDGE_DATA_RESIDENCY_CONTRACT.md §6):
 *
 * <ul>
 *   <li><b>CTR</b>: cash per card per business day ({@code ctr.businessDayTimezone}) in
 *       {@code ctr.reportingCurrency}, converted with {@code ctr.fx}. The transaction that takes the
 *       card-day total to the threshold or above sets {@code ctrRequired} (typology
 *       {@code CTR_REPORTABLE}); the total is kept in an atomic site counter, so concurrent nodes
 *       cannot both miss the crossing.</li>
 *   <li><b>Fail visibly, never under-report</b>: a cash transaction whose amount cannot be
 *       converted (no rate, no currency), a missing reporting-currency threshold, or an unreadable
 *       counter yields {@code HOLD}, {@code ctrRequired = true} and typology
 *       {@code CTR_FX_UNAVAILABLE} / {@code CTR_CONFIG_INVALID} / {@code CTR_AGGREGATE_UNAVAILABLE},
 *       the way the cloud's {@code RegulatoryComplianceService} answers HOLD on missing FX.</li>
 *   <li><b>Cash structuring</b>: the cloud detector — a cash amount in
 *       [{@code floorRatio} × threshold, threshold) with at least {@code minTransactions} such
 *       transactions on the card in 24 h (current included) → {@code HOLD},
 *       {@code CASH_STRUCTURING_24H}.</li>
 *   <li><b>Limits</b>: every applicable {@code limits[]} entry is checked against site counters
 *       (bucketed: PT1H 12 × 5 min, P1D 24 × 1 h, P7D 7 × 1 d, P30D 30 × 1 d); a breach applies the
 *       limit's action with typology {@code LIMIT_<limitId>}. Usage is recorded after the decision,
 *       except for BLOCKed transactions (a declined payment consumes no limit).</li>
 * </ul>
 *
 * <p>Before the first config document is accepted, CTR is off and there are no limits (§6.3).
 * Nothing here logs transaction values.
 */
public final class ComplianceOverlay {

    /** Counter set of CTR card-day totals. */
    static final String SET_CTR = "ctr_day";
    /** Counter set of limit usage. */
    static final String SET_LIMITS = "limit_usage";
    /** Reporting amounts are kept in micro-minor units so per-transaction rounding cannot drift. */
    static final int MICRO = 6;
    private static final String BIN_MICRO = "micro";
    private static final String BIN_COUNT = "n";
    private static final String BIN_AMOUNT = "amt";
    private static final int CTR_TTL = 4 * 86400;

    private final EdgeFeatureStore store;
    private final Supplier<Optional<EdgeConfigDocument>> config;

    /**
     * @param store  the site store holding the counters
     * @param config the Edge Config in force (empty before the first accepted document)
     */
    public ComplianceOverlay(EdgeFeatureStore store, Supplier<Optional<EdgeConfigDocument>> config) {
        this.store = store;
        this.config = config;
    }

    /**
     * The overlay's verdict for one transaction.
     *
     * @param features        compliance features exposed to the rules (cloud names)
     * @param action          the strongest action the checks require ({@code ALLOW} when none)
     * @param ctrRequired     decision record {@code ctrRequired}
     * @param structuringBand cash transaction in the structuring band (recorded in history)
     * @param typologyCodes   decision record {@code typologyCodes}
     * @param reasons         local reason texts (ledger only, never shipped)
     * @param limitUsage      limit counters to advance once the decision stands
     */
    public record Assessment(Map<String, Object> features, Action action, boolean ctrRequired, boolean structuringBand,
                             List<String> typologyCodes, List<String> reasons, List<Usage> limitUsage) {
    }

    /**
     * A pending limit usage increment.
     *
     * @param key        the current bucket's counter
     * @param ttlSeconds bucket TTL
     * @param amount     increment of the amount bin (limit-currency minor units; 0 for COUNT)
     */
    public record Usage(CounterKey key, int ttlSeconds, long amount) {
    }

    /** Mutable accumulator used while assessing. */
    private static final class Acc {
        final Map<String, Object> features = new LinkedHashMap<>();
        final List<String> codes = new ArrayList<>();
        final List<String> reasons = new ArrayList<>();
        final List<Usage> usage = new ArrayList<>();
        Action action = Action.ALLOW;
        boolean ctrRequired;
        boolean band;

        /** Raises the required action and records a typology code with its local reason. */
        void flag(Action a, String code, String reason) {
            action = action.max(a);
            if (!codes.contains(code)) {
                codes.add(code);
            }
            reasons.add(reason);
        }
    }

    /**
     * Runs CTR, structuring and limit checks before rule evaluation. The CTR card-day counter is
     * advanced here (atomically, with the total read back); limit usage is only read.
     *
     * @param t       the transaction
     * @param request the request feature bag (caller fields)
     * @param derived the history features ({@link CardHistoryFeatures})
     */
    public Assessment assess(TxnContext t, Map<String, Object> request, Map<String, Object> derived) {
        Acc acc = new Acc();
        Optional<EdgeConfigDocument> doc = config.get();
        boolean cash = isCash(request, t, doc);
        acc.features.put("cash_transaction", cash);
        requestFeatures(acc, request, cash);
        acc.features.put("ctr_required", false);
        acc.features.put("is_structuring_suspected", false);
        acc.features.put("structuring_repeat_count_24h", 0L);
        acc.features.put("structuring_amount_band", false);
        if (doc.isPresent()) {
            if (cash) {
                cashChecks(acc, t, derived, doc.get().ctr());
            }
            limits(acc, t, request, doc.get());
        }
        return new Assessment(acc.features, acc.action, acc.ctrRequired, acc.band, List.copyOf(acc.codes),
                List.copyOf(acc.reasons), List.copyOf(acc.usage));
    }

    /**
     * Records limit usage once the decision stands; a BLOCKed transaction consumes nothing. Never
     * throws (a counter outage is already visible as {@code LIMITS_UNAVAILABLE} on later reads).
     */
    public void commit(Assessment assessment, Action finalAction) {
        if (finalAction == Action.BLOCK) {
            return;
        }
        for (Usage u : assessment.limitUsage()) {
            Map<String, Long> inc = new LinkedHashMap<>();
            inc.put(BIN_COUNT, 1L);
            inc.put(BIN_AMOUNT, u.amount());
            store.addCounters(u.key(), u.ttlSeconds(), inc);
        }
    }

    /** Cash: an explicit caller flag, or a channel the config lists as cash. */
    static boolean isCash(Map<String, Object> request, TxnContext t, Optional<EdgeConfigDocument> doc) {
        if (truthy(request.get("cash_transaction")) || truthy(request.get("is_cash"))) {
            return true;
        }
        return t.channel() != null && doc.isPresent() && doc.get().ctr().cashChannels().contains(t.channel());
    }

    /** Request-level features the compiled catalog reads (reference keywords, anonymity). */
    private static void requestFeatures(Acc acc, Map<String, Object> request, boolean cash) {
        Object reference = request.get("reference");
        if (reference instanceof CharSequence cs) {
            acc.features.put("reference_lc", cs.toString().toLowerCase(Locale.ROOT));
        }
        Object cardType = request.get("card_type");
        boolean prepaid = cardType instanceof CharSequence cs && cs.toString().toUpperCase(Locale.ROOT).contains("PREPAID");
        boolean identified = !blank(request.get("customer_account_reference")) || !blank(request.get("account_id"))
                || !blank(request.get("customer_id")) || !blank(request.get("customer_email"));
        // The cloud's R-170 signal: prepaid instrument, cash, or no customer identification.
        acc.features.put("anonymous_payment_screening_hit", prepaid || cash || !identified);
    }

    /** CTR aggregation and cash structuring for one cash transaction. */
    private void cashChecks(Acc acc, TxnContext t, Map<String, Object> derived, EdgeConfigDocument.Ctr ctr) {
        String reporting = ctr.reportingCurrency();
        Long threshold = ctr.thresholds().get(reporting);
        if (threshold == null) {
            if (ctr.enabled()) {
                acc.ctrRequired = true;
                acc.features.put("ctr_required", true);
                acc.flag(Action.HOLD, "CTR_CONFIG_INVALID",
                        "CTR is enabled but the Edge config has no threshold for its reporting currency (fail visibly)");
            }
            return;
        }
        Optional<BigDecimal> converted = FxConversion.toMajor(t.amountCentsOrZero(), t.currency(), reporting, ctr);
        if (converted.isEmpty()) {
            if (ctr.enabled()) {
                acc.ctrRequired = true;
                acc.features.put("ctr_required", true);
            }
            acc.flag(Action.HOLD, "CTR_FX_UNAVAILABLE",
                    "cash amount cannot be converted to the reporting currency with the Edge config FX (fail visibly)");
            return;
        }
        int scale = FxConversion.digits(reporting) + MICRO;
        long micro = converted.get().movePointRight(scale).setScale(0, RoundingMode.CEILING).longValueExact();
        long thresholdMicro = BigDecimal.valueOf(threshold).movePointRight(MICRO).longValueExact();

        if (ctr.enabled()) {
            long total = micro;
            if (t.hasCard()) {
                LocalDate day = t.at().atZone(ctr.timezone()).toLocalDate();
                Map<String, Long> inc = new LinkedHashMap<>();
                inc.put(BIN_MICRO, micro);
                inc.put(BIN_COUNT, 1L);
                Optional<long[]> after = store.addCounters(
                        new CounterKey(SET_CTR, t.panHash() + ":" + day.toString().replace("-", "")), CTR_TTL, inc);
                if (after.isEmpty()) {
                    acc.ctrRequired = true;
                    acc.features.put("ctr_required", true);
                    acc.flag(Action.HOLD, "CTR_AGGREGATE_UNAVAILABLE",
                            "the card-day cash total cannot be read from the site store (fail visibly)");
                    return;
                }
                total = after.get()[0];
            }
            boolean crossed = total >= thresholdMicro && total - micro < thresholdMicro;
            if (crossed) {
                acc.ctrRequired = true;
                acc.features.put("ctr_required", true);
                acc.flag(Action.ALLOW, "CTR_REPORTABLE", "card-day cash total reached the CTR threshold");
            }
        }

        BigDecimal floorMicro = BigDecimal.valueOf(thresholdMicro).multiply(ctr.structuringFloorRatio());
        boolean band = micro < thresholdMicro && BigDecimal.valueOf(micro).compareTo(floorMicro) >= 0;
        acc.band = band && t.hasCard();
        acc.features.put("structuring_amount_band", band);
        if (band && t.hasCard()) {
            long prior = derived.get(CardHistoryFeatures.CASH_BAND_COUNT_24H) instanceof Number n ? n.longValue() : 0L;
            long repeat = prior + 1;
            acc.features.put("structuring_repeat_count_24h", repeat);
            if (repeat >= ctr.structuringMinTransactions()) {
                acc.features.put("is_structuring_suspected", true);
                acc.flag(Action.HOLD, "CASH_STRUCTURING_24H",
                        "repeated cash transactions fall immediately below the reporting threshold");
            }
        }
    }

    /** Checks every applicable limit against its window counters in one batch read. */
    private void limits(Acc acc, TxnContext t, Map<String, Object> request, EdgeConfigDocument doc) {
        if (doc.limits().isEmpty()) {
            return;
        }
        String mcc = mcc(request.get("mcc"));
        List<EdgeConfigDocument.Limit> applicable = new ArrayList<>();
        List<String> scopeKeys = new ArrayList<>();
        List<Long> contributions = new ArrayList<>();
        for (EdgeConfigDocument.Limit l : doc.limits()) {
            if (l.merchantId() != null && !l.merchantId().equals(t.merchantId())) {
                continue;
            }
            if (l.mcc() != null && !l.mcc().equals(mcc)) {
                continue;
            }
            String scopeKey = switch (l.scope()) {
                case "CARD" -> t.panHash();
                case "MERCHANT" -> t.merchantId();
                default -> "psp";
            };
            if (scopeKey == null) {
                continue;
            }
            long contribution = 1L;
            if ("AMOUNT".equals(l.metric())) {
                Optional<BigDecimal> converted = FxConversion.toMajor(t.amountCentsOrZero(), t.currency(), l.currency(),
                        doc.ctr());
                if (converted.isEmpty()) {
                    acc.flag(Action.HOLD, "LIMIT_FX_UNAVAILABLE",
                            "limit " + l.limitId() + " cannot be evaluated: no FX rate to its currency (fail visibly)");
                    continue;
                }
                contribution = converted.get().movePointRight(FxConversion.digits(l.currency()))
                        .setScale(0, RoundingMode.CEILING).longValueExact();
            }
            applicable.add(l);
            scopeKeys.add(scopeKey);
            contributions.add(contribution);
        }
        if (applicable.isEmpty()) {
            return;
        }
        List<CounterKey> keys = new ArrayList<>();
        List<int[]> ranges = new ArrayList<>();
        for (int i = 0; i < applicable.size(); i++) {
            Window w = Window.of(applicable.get(i).window());
            int start = keys.size();
            long current = w.bucket(t.at().getEpochSecond());
            for (int b = 0; b < w.buckets; b++) {
                keys.add(limitKey(applicable.get(i), scopeKeys.get(i), w, current - b));
            }
            ranges.add(new int[]{start, keys.size()});
        }
        Optional<long[][]> read = store.readCounters(keys, List.of(BIN_COUNT, BIN_AMOUNT));
        if (read.isEmpty()) {
            acc.flag(Action.HOLD, "LIMITS_UNAVAILABLE", "limit usage cannot be read from the site store (fail visibly)");
            return;
        }
        for (int i = 0; i < applicable.size(); i++) {
            EdgeConfigDocument.Limit l = applicable.get(i);
            boolean amount = "AMOUNT".equals(l.metric());
            long used = 0;
            for (int k = ranges.get(i)[0]; k < ranges.get(i)[1]; k++) {
                used += read.get()[k][amount ? 1 : 0];
            }
            long contribution = contributions.get(i);
            if (used + contribution > l.max()) {
                acc.flag(Action.valueOf(l.action()), "LIMIT_" + l.limitId(),
                        "limit " + l.limitId() + " exceeded (" + l.scope() + " " + l.metric() + " " + l.window() + ")");
            }
            Window w = Window.of(l.window());
            acc.usage.add(new Usage(keys.get(ranges.get(i)[0]), w.ttlSeconds(), amount ? contribution : 0L));
        }
    }

    /** The counter key of one limit bucket. */
    private static CounterKey limitKey(EdgeConfigDocument.Limit l, String scopeKey, Window w, long bucket) {
        return new CounterKey(SET_LIMITS, "L" + l.limitId() + ":" + scopeKey + ":" + w.code + bucket);
    }

    /** Bucketing of a limit window. */
    enum Window {
        /** One hour as 12 five-minute buckets. */
        PT1H("m", 300, 12),
        /** One day as 24 hourly buckets. */
        P1D("h", 3600, 24),
        /** Seven days as 7 daily buckets. */
        P7D("d", 86400, 7),
        /** Thirty days as 30 daily buckets. */
        P30D("d", 86400, 30);

        final String code;
        final long seconds;
        final int buckets;

        /** @param code key prefix, @param seconds bucket width, @param buckets buckets per window */
        Window(String code, long seconds, int buckets) {
            this.code = code;
            this.seconds = seconds;
            this.buckets = buckets;
        }

        /** The window of a config value. */
        static Window of(String value) {
            return Window.valueOf(value);
        }

        /** Bucket number of an epoch second. */
        long bucket(long epochSecond) {
            return Math.floorDiv(epochSecond, seconds);
        }

        /** Bucket TTL: the window plus one bucket of slack. */
        int ttlSeconds() {
            return (int) (seconds * (buckets + 1));
        }
    }

    /** MCC as 4 digits (integers zero-padded), or null. */
    private static String mcc(Object v) {
        if (v instanceof Integer || v instanceof Long || v instanceof Short) {
            long n = ((Number) v).longValue();
            return n >= 0 && n <= 9999 ? String.format("%04d", n) : null;
        }
        return v instanceof CharSequence cs ? cs.toString().trim() : null;
    }

    /** JSON truthiness of a flag (true or "true"). */
    private static boolean truthy(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof CharSequence cs && "true".equalsIgnoreCase(cs.toString().trim()));
    }

    /** Absent, null or blank. */
    private static boolean blank(Object v) {
        return v == null || (v instanceof CharSequence cs && cs.toString().isBlank());
    }
}
