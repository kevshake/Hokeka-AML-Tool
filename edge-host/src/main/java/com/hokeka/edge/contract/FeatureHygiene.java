package com.hokeka.edge.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Feature-bag hygiene of EDGE_DATA_RESIDENCY_CONTRACT.md §2.4, applied before anything is written
 * to {@code edge_evaluations.features}.
 *
 * <ol>
 *   <li>drop the card-secret keys (and {@code pan_hash}, which lives in its own column);</li>
 *   <li>drop any key that is not {@code snake_case} ({@code ^[a-z][a-z0-9_]{0,63}$}), any non-scalar
 *       value and any string value over 512 characters;</li>
 *   <li>drop any string value the Luhn guard flags (and, stricter than the contract, any integral
 *       number the guard flags, so a PAN sent as a JSON number cannot slip through).</li>
 * </ol>
 *
 * <p>The result also respects the tunnel schema's {@code maxProperties: 256}
 * ({@code common.schema.json#/$defs/features}): keys beyond the first 256 that survive hygiene are
 * dropped, in request order, so a stored bag can always be returned by {@code txn.get}.
 */
public final class FeatureHygiene {

    /** Keys that MUST NOT be stored (§2.4 step 1). */
    public static final Set<String> FORBIDDEN_KEYS = Set.of(
            "pan", "pan_hash", "card_number", "pan_masked_full", "cvv", "cvv2", "cvc", "cvc2",
            "track1", "track2", "track_data", "pin", "pin_block", "expiry", "exp_date", "card_expiry");

    /** {@code common.schema.json#/$defs/featureName}. */
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** Longest string value kept (§2.4 step 2). */
    public static final int MAX_STRING_LENGTH = 512;

    /** {@code maxProperties} of the tunnel {@code features} schema. */
    public static final int MAX_FEATURES = 256;

    private FeatureHygiene() {
    }

    /**
     * Returns a new map holding only the entries that may be stored on the Edge Ledger.
     *
     * @param features the enriched feature bag that was evaluated; {@code null} yields an empty map
     * @return a fresh, insertion-ordered map; the input is never modified
     */
    public static Map<String, Object> sanitize(Map<String, Object> features) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (features == null) {
            return clean;
        }
        for (Map.Entry<String, Object> e : features.entrySet()) {
            if (clean.size() >= MAX_FEATURES) {
                break;
            }
            String key = e.getKey();
            if (key == null || FORBIDDEN_KEYS.contains(key) || !KEY.matcher(key).matches()) {
                continue;
            }
            Object value = e.getValue();
            if (keepValue(value)) {
                clean.put(key, value);
            }
        }
        return clean;
    }

    /** Scalar check plus the length and Luhn rules for one value (§2.4 steps 2 and 3). */
    private static boolean keepValue(Object value) {
        if (value == null || value instanceof Boolean) {
            return true;
        }
        if (value instanceof CharSequence cs) {
            String s = cs.toString();
            return s.length() <= MAX_STRING_LENGTH && !LuhnGuard.panLike(s);
        }
        if (value instanceof Number n) {
            if (n instanceof Double d && (d.isNaN() || d.isInfinite())) {
                return false; // not representable in JSON
            }
            if (n instanceof Float f && (f.isNaN() || f.isInfinite())) {
                return false;
            }
            return !LuhnGuard.panLike(n);
        }
        // Maps, lists, arrays and any other object are non-scalar.
        return false;
    }
}
