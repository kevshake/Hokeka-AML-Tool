package com.hokeka.edge.contract;

/**
 * The PAN guard of EDGE_DATA_RESIDENCY_CONTRACT.md §2.5.
 *
 * <p>A string is <b>PAN-like</b> when, after removing spaces and hyphens, it is 13 to 19 decimal
 * digits and passes the Luhn check. The contract schemas mark guarded fields with the extension
 * keyword {@code x-hokeka-luhnReject}; generic validators ignore it, so every runtime implements the
 * guard explicitly (§13). This class is that implementation for the Edge; it is used by the
 * {@code /edge/evaluate} {@code txn_id} check, by feature-bag hygiene (§2.4) and by the schema
 * validator's custom keyword ({@link ContractSchemas}).
 */
public final class LuhnGuard {

    private LuhnGuard() {
    }

    /**
     * Whether {@code value} looks like a card number (§2.5).
     *
     * @param value any string; {@code null} is never PAN-like
     * @return true when the value, stripped of spaces and hyphens, is 13..19 digits passing Luhn
     */
    public static boolean panLike(String value) {
        if (value == null) {
            return false;
        }
        int len = value.length();
        // Fast exit for the overwhelmingly common case: far too short or far too long to be a PAN
        // even with separators. 19 digits + at most one separator between each digit is 37 chars.
        if (len < 13 || len > 37) {
            return false;
        }
        int digits = 0;
        int[] buffer = new int[19];
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            if (c < '0' || c > '9') {
                return false;
            }
            if (digits == 19) {
                return false; // more than 19 digits
            }
            buffer[digits++] = c - '0';
        }
        if (digits < 13) {
            return false;
        }
        return luhnValid(buffer, digits);
    }

    /**
     * Whether an integral JSON number looks like a card number: the same rule applied to its decimal
     * digits. Used by feature hygiene so a PAN smuggled as a number is dropped as well (fail closed,
     * invariant I8), which is stricter than the string-only rule of §2.4 step 3.
     *
     * @param value an integral number, or any other number (non-integral values are never PAN-like)
     * @return true when the number's digits form a PAN-like value
     */
    public static boolean panLike(Number value) {
        if (value == null) {
            return false;
        }
        String digits;
        if (value instanceof java.math.BigInteger big) {
            digits = big.abs().toString();
        } else if (value instanceof java.math.BigDecimal dec) {
            if (dec.signum() != 0 && dec.stripTrailingZeros().scale() > 0) {
                return false;
            }
            digits = dec.toBigInteger().abs().toString();
        } else if (value instanceof Double || value instanceof Float) {
            double d = value.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                return false;
            }
            digits = new java.math.BigDecimal(d).toBigInteger().abs().toString();
        } else {
            digits = Long.toString(Math.abs(value.longValue()));
        }
        return panLike(digits);
    }

    /** Standard Luhn mod-10 check over {@code count} digits held in {@code d}. */
    private static boolean luhnValid(int[] d, int count) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = count - 1; i >= 0; i--) {
            int v = d[i];
            if (doubleIt) {
                v *= 2;
                if (v > 9) {
                    v -= 9;
                }
            }
            sum += v;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
