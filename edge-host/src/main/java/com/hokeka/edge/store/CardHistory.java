package com.hokeka.edge.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Edge knows about a card's (and its device's / IP's) past at evaluation time, read
 * from the site's Aerospike store in one batch. It never includes the transaction being evaluated:
 * counters advance only after evaluation. {@link CardHistoryFeatures} turns it into rule features.
 *
 * @param hours      hourly buckets, index 0 = the current UTC hour, up to {@link #HOURS} back
 * @param days       daily buckets, index 0 = the current UTC day, up to {@link #DAYS} back
 * @param firstSeenMs first evaluation of this card on the site (epoch ms), or 0 when none
 * @param lastSeenMs  latest evaluation of this card on the site (epoch ms), or 0 when none
 * @param recent      the card's latest evaluations, newest first, at most {@link #RECENT_MAX}
 * @param ipRecent    latest evaluations seen with this IP address, newest first
 * @param deviceRecent latest evaluations seen with this device, newest first
 */
public record CardHistory(List<Hour> hours, List<Day> days, long firstSeenMs, long lastSeenMs,
                          List<Entry> recent, List<Entry> ipRecent, List<Entry> deviceRecent) {

    /** Hourly buckets read: today's day-over-day ratio needs the previous 24 h as well. */
    public static final int HOURS = 48;
    /** Daily buckets read: the 30-day baselines plus the current day. */
    public static final int DAYS = 31;
    /** Recent evaluations kept per card / device / IP (bounded, so reads stay O(1)). */
    public static final int RECENT_MAX = 100;

    /** An empty history (no store or a new card). */
    public static CardHistory empty() {
        List<Hour> h = new ArrayList<>();
        for (int i = 0; i < HOURS; i++) {
            h.add(Hour.ZERO);
        }
        List<Day> d = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            d.add(Day.ZERO);
        }
        return new CardHistory(h, d, 0L, 0L, List.of(), List.of(), List.of());
    }

    /**
     * One hourly bucket.
     *
     * @param count     evaluations
     * @param cents     amount sum (minor units)
     * @param amountN   evaluations that carried an amount
     * @param inCount   inbound evaluations
     * @param outCount  outbound evaluations
     * @param inCents   inbound amount sum
     * @param outCents  outbound amount sum
     * @param bandCount cash transactions in the structuring band
     */
    public record Hour(long count, long cents, long amountN, long inCount, long outCount, long inCents,
                       long outCents, long bandCount) {
        /** The empty bucket. */
        public static final Hour ZERO = new Hour(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * One daily bucket.
     *
     * @param count    evaluations
     * @param cents    amount sum
     * @param amountN  evaluations that carried an amount
     * @param squares  sum of squared amounts (cents²), for the z-score variance
     * @param inCount  inbound evaluations
     * @param outCount outbound evaluations
     */
    public record Day(long count, long cents, long amountN, double squares, long inCount, long outCount) {
        /** The empty bucket. */
        public static final Day ZERO = new Day(0, 0, 0, 0.0, 0, 0);
    }

    /**
     * One remembered evaluation.
     *
     * @param atMs       evaluation time (epoch ms)
     * @param cents      amount, or null
     * @param merchantId merchant, or null
     * @param country    country code, or null
     * @param currency   currency, or null
     * @param inLike     raw direction read as money in
     * @param device     device key, or null
     * @param brand      card brand, or null
     */
    public record Entry(long atMs, Long cents, String merchantId, String country, String currency, boolean inLike,
                        String device, String brand) {

        /** The entry for the transaction being recorded. */
        public static Entry of(TxnContext t) {
            return new Entry(t.at().toEpochMilli(), t.amountCents(), t.merchantId(), t.countryCode(), t.currency(),
                    t.inLike(), t.deviceKey(), t.cardBrand());
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Encodes entries as a compact JSON array of arrays (the Aerospike {@code recent} bin). */
    public static String encode(List<Entry> entries) {
        ArrayNode root = MAPPER.createArrayNode();
        for (Entry e : entries) {
            ArrayNode a = root.addArray();
            a.add(e.atMs());
            if (e.cents() == null) {
                a.addNull();
            } else {
                a.add(e.cents());
            }
            addText(a, e.merchantId());
            addText(a, e.country());
            addText(a, e.currency());
            a.add(e.inLike() ? 1 : 0);
            addText(a, e.device());
            addText(a, e.brand());
        }
        return root.toString();
    }

    /** Appends a text or null. */
    private static void addText(ArrayNode a, String s) {
        if (s == null) {
            a.addNull();
        } else {
            a.add(s);
        }
    }

    /** Decodes {@link #encode}'s output; an unreadable bin decodes as no entries. */
    public static List<Entry> decode(String json) {
        List<Entry> out = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return out;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            for (JsonNode a : root) {
                out.add(new Entry(a.get(0).asLong(), a.get(1).isNull() ? null : a.get(1).asLong(),
                        textAt(a, 2), textAt(a, 3), textAt(a, 4), a.get(5).asInt() == 1, textAt(a, 6),
                        textAt(a, 7)));
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return out;
    }

    /** Text at an index, or null. */
    private static String textAt(JsonNode a, int i) {
        JsonNode n = a.get(i);
        return n == null || n.isNull() ? null : n.asText();
    }

    /** {@code entry} prepended to {@code entries}, keeping the newest {@link #RECENT_MAX}. */
    public static List<Entry> prepend(Entry entry, List<Entry> entries) {
        List<Entry> out = new ArrayList<>(Math.min(RECENT_MAX, entries.size() + 1));
        out.add(entry);
        for (Entry e : entries) {
            if (out.size() >= RECENT_MAX) {
                break;
            }
            out.add(e);
        }
        return out;
    }
}
