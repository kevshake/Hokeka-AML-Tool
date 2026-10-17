package com.hokeka.edge.store;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives the typology features the compiled SpEL catalog reads, from a card's
 * {@link CardHistory} and the transaction being evaluated. Pure and I/O free.
 *
 * <p>Names, presence rules and arithmetic follow the cloud producer
 * ({@code BACKEND RuleFeatureEnrichmentService}): cloud windows that end at {@code <= now} include
 * the current transaction (the cloud stores it before scoring), so the Edge adds it; windows that
 * end at {@code < now} ("Before") exclude it. A feature the cloud only sets conditionally is only
 * set here under the same condition, so a guarded rule ({@code x != null && ...}) behaves the same.
 *
 * <p>Window resolution (documented in docs/edge-transaction-evaluation.md §2.2):
 * 1 minute / 5 minutes / 1 hour are rolling and exact from the card's last
 * {@link CardHistory#RECENT_MAX} evaluations; 24 h is the last 24 UTC hourly buckets; "yesterday"
 * is buckets 24..47; 7 and 30 days are the current plus 6 / 29 previous UTC days.
 */
public final class CardHistoryFeatures {

    /** Earlier structuring-band cash transactions of the card in the last 24 hourly buckets. */
    public static final String CASH_BAND_COUNT_24H = "cash_band_count_24h";

    private CardHistoryFeatures() {
    }

    /** Features computable from the request alone (no history). */
    public static Map<String, Object> deriveRequestOnly(TxnContext t) {
        Map<String, Object> f = new LinkedHashMap<>();
        long cur = t.amountCentsOrZero();
        // The cloud reads amount in major units (cents / 100, 0 when absent).
        f.put("amount", cur / 100.0);
        f.put("amount_cents", cur);
        f.put("txn_direction", t.direction());
        if (t.amountCents() != null) {
            long cents = Math.abs(t.amountCents() % 100);
            f.put("amount_ending_pattern", cents == 99 || cents == 0);
        }
        if (t.deviceKey() != null) {
            f.put("device_key", t.deviceKey());
        }
        return f;
    }

    /** Derives the full feature set for one evaluation. */
    public static Map<String, Object> derive(CardHistory h, TxnContext t) {
        Map<String, Object> f = deriveRequestOnly(t);
        long nowMs = t.at().toEpochMilli();
        long cur = t.amountCentsOrZero();
        if (!t.hasCard()) {
            deviceAndIp(f, h, t);
            return f;
        }

        // Velocity, current transaction included (cloud countByPanInTimeWindow is <= now).
        f.put("pan_txn_count_1m", rollingCount(h, nowMs, Duration.ofMinutes(1)) + 1);
        f.put("pan_txn_count_5m", rollingCount(h, nowMs, Duration.ofMinutes(5)) + 1);
        long count1h = rollingCount(h, nowMs, Duration.ofHours(1)) + 1;
        long sum1h = rollingSum(h, nowMs, Duration.ofHours(1)) + cur;
        f.put("pan_txn_count_1h", count1h);
        long count24 = 1;
        long sum24 = cur;
        long in24 = t.inbound() ? cur : 0;
        long out24 = t.inbound() ? 0 : cur;
        long prior24Count = 0;
        long prior24Sum = 0;
        long prior24AmountN = 0;
        long yesterday = 0;
        for (int i = 0; i < CardHistory.HOURS; i++) {
            CardHistory.Hour b = h.hours().get(i);
            if (i < 24) {
                count24 += b.count();
                sum24 += b.cents();
                in24 += b.inCents();
                out24 += b.outCents();
                prior24Count += b.count();
                prior24Sum += b.cents();
                prior24AmountN += b.amountN();
            } else {
                yesterday += b.count();
            }
        }
        f.put("pan_txn_count_24h", count24);
        f.put("pan_amount_sum_24h", sum24 / 100.0);
        long band24 = 0;
        for (int i = 0; i < 24; i++) {
            band24 += h.hours().get(i).bandCount();
        }
        // Earlier cash transactions of this card in the structuring band (read by ComplianceOverlay).
        f.put(CASH_BAND_COUNT_24H, band24);

        // First / last activity (cloud: count over 10 years incl. current <= 1; last txn strictly before).
        boolean first = h.firstSeenMs() == 0L;
        f.put("is_first_transaction", first);
        f.put("days_since_last_activity", h.lastSeenMs() == 0L ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(h.lastSeenMs()), t.at())));

        // New country / currency versus the card's evaluations in the last 30 days (excluding this one).
        long since30 = nowMs - Duration.ofDays(30).toMillis();
        String country = t.countryCode() == null ? "UNK" : t.countryCode();
        f.put("is_new_country", h.recent().stream()
                .noneMatch(e -> e.atMs() >= since30 && country.equals(e.country())));
        if (t.currency() != null) {
            f.put("is_new_currency", h.recent().stream()
                    .noneMatch(e -> e.atMs() >= since30 && t.currency().equals(e.currency())));
        }

        // 7 / 30 day baselines from daily buckets.
        long c7 = 1;
        long c30 = 1;
        long s7 = cur;
        long s30 = cur;
        long in7 = t.inbound() ? 1 : 0;
        long out7 = t.inbound() ? 0 : 1;
        long prior7Sum = 0;
        long prior7N = 0;
        long prior30Sum = 0;
        long prior30N = 0;
        double prior30Squares = 0.0;
        for (int i = 0; i < 30 && i < h.days().size(); i++) {
            CardHistory.Day d = h.days().get(i);
            c30 += d.count();
            s30 += d.cents();
            prior30Sum += d.cents();
            prior30N += d.amountN();
            prior30Squares += d.squares();
            if (i < 7) {
                c7 += d.count();
                s7 += d.cents();
                in7 += d.inCount();
                out7 += d.outCount();
                prior7Sum += d.cents();
                prior7N += d.amountN();
            }
        }
        if (c30 > 5) {
            double baselineDaily = c30 / 30.0;
            f.put("txn_count_deviation_ratio", (c7 / 7.0) / baselineDaily);
        }
        if (s30 > 0) {
            f.put("volume_deviation_ratio", (s7 / 7.0) / (s30 / 30.0));
        }
        double avg30 = prior30N > 0 ? (double) prior30Sum / prior30N : 0.0;
        if (avg30 > 0) {
            f.put("avg_amount_spike_ratio", cur / avg30);
        }
        f.put("zscore_amount_vs_pan_history", zscore(t.amountCents(), prior30N, prior30Sum, prior30Squares));

        // Window ratios (cloud enrichAdvancedVelocityRatios).
        if (sum24 > 0) {
            double prior23h = (sum24 - sum1h) / 23.0;
            if (prior23h > 0) {
                f.put("volume_ratio_t1_t2", sum1h / prior23h);
            }
        }
        if (count24 > 0) {
            f.put("txn_count_window_ratio", count1h / (count24 / 24.0));
        }
        f.put("daily_txn_count_ratio", yesterday > 0 ? (double) count24 / yesterday : (double) count24);
        double avg7 = prior7N > 0 ? (double) prior7Sum / prior7N : 0.0;
        double avg1 = prior24AmountN > 0 ? (double) prior24Sum / prior24AmountN : 0.0;
        if (avg7 > 0) {
            f.put("avg_amount_window_ratio", avg1 / avg7);
            f.put("daily_avg_amount_ratio", avg1 / avg7);
        }
        if (in7 > 0) {
            f.put("send_receive_ratio", (double) out7 / in7);
        }
        if (in24 > 0) {
            f.put("spend_receive_spike_ratio", (double) out24 / in24);
        }

        // Recent-evaluation features (cloud: last N transactions before now, within 7 days).
        long since7 = nowMs - Duration.ofDays(7).toMillis();
        List<CardHistory.Entry> week = h.recent().stream().filter(e -> e.atMs() >= since7).toList();
        if (t.merchantId() != null) {
            long same = cur;
            for (CardHistory.Entry e : week) {
                if (t.merchantId().equals(e.merchantId()) && e.cents() != null) {
                    same += e.cents();
                }
            }
            f.put("same_parties_volume", same / 100.0);
        }
        Map<String, Integer> perMerchant = new HashMap<>();
        for (CardHistory.Entry e : week) {
            if (e.merchantId() != null) {
                perMerchant.merge(e.merchantId(), 1, Integer::sum);
            }
        }
        f.put("circular_trading_count", perMerchant.values().stream().filter(n -> n >= 2).count());
        if (!week.isEmpty()) {
            long refunds = week.stream().filter(CardHistory.Entry::inLike).count();
            f.put("refund_share_pct", refunds * 100.0 / week.size());
        }
        Set<String> merchants = new HashSet<>(perMerchant.keySet());
        Set<String> brands = new HashSet<>();
        Set<String> devices = new HashSet<>();
        for (CardHistory.Entry e : week) {
            if (e.brand() != null) {
                brands.add(e.brand());
            }
            if (e.device() != null) {
                devices.add(e.device());
            }
        }
        if (t.merchantId() != null) {
            merchants.add(t.merchantId());
        }
        if (t.cardBrand() != null) {
            brands.add(t.cardBrand());
        }
        if (t.deviceKey() != null) {
            devices.add(t.deviceKey());
        }
        f.put("unique_senders", (long) merchants.size());
        f.put("unique_counterparties", (long) merchants.size());
        f.put("distinct_banks", (long) brands.size());
        f.put("bank_name_changes", (long) Math.max(0, brands.size() - 1));
        f.put("distinct_payment_ids", (long) devices.size());

        List<CardHistory.Entry> last50 = week.size() > 50 ? week.subList(0, 50) : week;
        if (!last50.isEmpty()) {
            long round = last50.stream().filter(e -> e.cents() != null && e.cents() % 100 == 0).count();
            f.put("round_value_share_pct", round * 100.0 / last50.size());
            f.put("round_value_txn_count", round);
        }
        deviceAndIp(f, h, t);
        return f;
    }

    /** Device and IP features (cloud: distinct merchants per device / IP, IP use and country spread). */
    private static void deviceAndIp(Map<String, Object> f, CardHistory h, TxnContext t) {
        if (t.deviceKey() != null) {
            Set<String> merchants = new HashSet<>();
            h.deviceRecent().forEach(e -> {
                if (e.merchantId() != null) {
                    merchants.add(e.merchantId());
                }
            });
            if (t.merchantId() != null) {
                merchants.add(t.merchantId());
            }
            f.put("distinct_users_per_payment_id", merchants.size());
        }
        if (t.ipAddress() != null) {
            long since7 = t.at().toEpochMilli() - Duration.ofDays(7).toMillis();
            Set<String> merchants = new HashSet<>();
            Set<String> countries = new HashSet<>();
            long uses7 = 1;
            for (CardHistory.Entry e : h.ipRecent()) {
                if (e.merchantId() != null) {
                    merchants.add(e.merchantId());
                }
                if (e.country() != null) {
                    countries.add(e.country());
                }
                if (e.atMs() >= since7) {
                    uses7++;
                }
            }
            if (t.merchantId() != null) {
                merchants.add(t.merchantId());
            }
            if (t.countryCode() != null) {
                countries.add(t.countryCode());
            }
            f.put("distinct_users_per_ip", merchants.size());
            f.put("payment_id_use_count", uses7);
            f.put("distinct_countries", countries.size());
            f.put("ip_outside_expected_location", countries.size() > 2);
            f.put("ip_change_count", countries.size());
        }
    }

    /** Count of the card's earlier evaluations within {@code window} before now (rolling, exact). */
    static long rollingCount(CardHistory h, long nowMs, Duration window) {
        long since = nowMs - window.toMillis();
        long n = h.recent().stream().filter(e -> e.atMs() >= since && e.atMs() <= nowMs).count();
        return saturated(h, since) ? Math.max(n, bucketUpperBound(h, window, true)) : n;
    }

    /** Amount sum of the card's earlier evaluations within {@code window} (rolling, exact). */
    static long rollingSum(CardHistory h, long nowMs, Duration window) {
        long since = nowMs - window.toMillis();
        long s = 0;
        for (CardHistory.Entry e : h.recent()) {
            if (e.atMs() >= since && e.atMs() <= nowMs && e.cents() != null) {
                s += e.cents();
            }
        }
        return saturated(h, since) ? Math.max(s, bucketUpperBound(h, window, false)) : s;
    }

    /**
     * True when the remembered list is full and its oldest entry is still inside the window, so the
     * list may have dropped evaluations of the window.
     */
    private static boolean saturated(CardHistory h, long since) {
        List<CardHistory.Entry> r = h.recent();
        return r.size() >= CardHistory.RECENT_MAX && r.get(r.size() - 1).atMs() >= since;
    }

    /** Upper bound from the current and previous hourly buckets, used only when the list saturates. */
    private static long bucketUpperBound(CardHistory h, Duration window, boolean count) {
        long v = 0;
        for (int i = 0; i < 2 && i < h.hours().size(); i++) {
            CardHistory.Hour b = h.hours().get(i);
            v += count ? b.count() : b.cents();
        }
        return v;
    }

    /**
     * Z-score of the current amount against the card's 30-day amounts (population variance), 0 when
     * there is no history, no current amount or no spread — the cloud's
     * {@code zscore_amount_vs_pan_history}.
     */
    static double zscore(Long amountCents, long n, long sum, double squares) {
        if (amountCents == null || n <= 0) {
            return 0.0;
        }
        double mean = (double) sum / n;
        double variance = Math.max(0.0, squares / n - mean * mean);
        double sd = Math.sqrt(variance);
        // Guard against floating-point residue standing in for a real spread.
        if (sd <= 1e-9 * Math.max(1.0, Math.abs(mean))) {
            return 0.0;
        }
        return (amountCents - mean) / sd;
    }
}
