package com.hokeka.edge.store;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * The transaction attributes the Edge derivations read, parsed once from the {@code /edge/evaluate}
 * feature bag. Field names follow the request contract (docs/edge-transaction-evaluation.md §2)
 * and the {@code txn.related} link keys of the residency contract (§7.6).
 *
 * @param at              evaluation instant (the transaction time for every window)
 * @param panHash         card key, or null
 * @param amountCents     amount in minor units, or null when absent or not a non-negative integer
 * @param currency        upper-case ISO 4217 code, or null
 * @param countryCode     upper-case country code, or null
 * @param merchantId      merchant id text, or null
 * @param channel         channel text (upper-case), or null
 * @param direction       normalised direction, {@code INBOUND} or {@code OUTBOUND} (absent → OUTBOUND,
 *                        as the cloud normalises it)
 * @param inLike          the raw direction reads as money in (contains IN / CREDIT / REFUND), the
 *                        cloud's refund-share test
 * @param deviceKey       {@code device_id}, else {@code device_fingerprint}, or null
 * @param ipAddress       {@code ip_address}, or null
 * @param cardBrand       {@code card_brand}, or null
 */
public record TxnContext(Instant at, String panHash, Long amountCents, String currency, String countryCode,
                         String merchantId, String channel, String direction, boolean inLike, String deviceKey,
                         String ipAddress, String cardBrand) {

    /** Normalised inbound direction. */
    public static final String INBOUND = "INBOUND";
    /** Normalised outbound direction. */
    public static final String OUTBOUND = "OUTBOUND";

    /**
     * Parses the attributes from a request feature bag.
     *
     * @param features the request body
     * @param at       evaluation instant
     */
    public static TxnContext from(Map<String, Object> features, Instant at) {
        String rawDirection = text(features.get("direction"));
        return new TxnContext(at,
                text(features.get("pan_hash")),
                amountCents(features.get("amount_cents")),
                upper(text(features.get("currency"))),
                upper(text(features.get("country_code"))),
                text(features.get("merchant_id")),
                upper(text(features.get("channel"))),
                normaliseDirection(rawDirection),
                rawDirection != null && inLike(rawDirection),
                firstText(features.get("device_id"), features.get("device_fingerprint")),
                text(features.get("ip_address")),
                text(features.get("card_brand")));
    }

    /** The cloud's direction normalisation: starts with IN or equals CREDIT → INBOUND, else OUTBOUND. */
    static String normaliseDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return OUTBOUND;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        return upper.startsWith("IN") || "CREDIT".equals(upper) ? INBOUND : OUTBOUND;
    }

    /** The cloud's refund-share test on a raw direction. */
    static boolean inLike(String raw) {
        String upper = raw.toUpperCase(Locale.ROOT);
        return upper.contains("IN") || upper.contains("CREDIT") || upper.contains("REFUND");
    }

    /** True when a card key is present. */
    public boolean hasCard() {
        return panHash != null;
    }

    /** {@code amount_cents}, or 0 when absent. */
    public long amountCentsOrZero() {
        return amountCents == null ? 0L : amountCents;
    }

    /** True for an inbound transaction. */
    public boolean inbound() {
        return INBOUND.equals(direction);
    }

    /** Text of a scalar value, or null for absent / blank / non-scalar. */
    static String text(Object v) {
        if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) {
            String s = v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                    && !(v instanceof Double) && !(v instanceof Float) ? Long.toString(n.longValue()) : v.toString();
            s = s.trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    /** The first non-blank text among the values. */
    private static String firstText(Object... values) {
        for (Object v : values) {
            String s = text(v);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /** Upper-cased text, or null. */
    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    /** {@code amount_cents} as a non-negative integer, or null (same rule as the ledger column). */
    static Long amountCents(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            long v = ((Number) value).longValue();
            return v >= 0 ? v : null;
        }
        if (value instanceof java.math.BigInteger big) {
            return big.signum() >= 0 && big.bitLength() < 63 ? big.longValue() : null;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d >= 0 && d == Math.rint(d) && d < 9.0e18 ? (long) d : null;
        }
        if (value instanceof CharSequence cs) {
            try {
                long v = Long.parseLong(cs.toString().trim());
                return v >= 0 ? v : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
