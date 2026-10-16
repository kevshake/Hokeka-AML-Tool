package com.posgateway.aml.util;

/**
 * Guard for {@code pan_hash} columns: they must only ever hold a keyed hash / token
 * (e.g. the 64-char hex HMAC from {@code PiiLookupHasher}) or the {@code UNKNOWN} sentinel,
 * never a raw card number.
 *
 * <p>A value "looks like a raw PAN" when, after removing spaces and dashes, it is 13–19
 * decimal digits. Digit-only strings of that length are rejected whether or not they pass
 * the Luhn check: a mistyped PAN is still card data, and no legitimate hash/token format in
 * this platform is a 13–19 digit number. {@link #isLuhnValid(String)} is exposed so callers
 * can report whether a rejected value was a checksum-valid card number.
 */
public final class PanHashGuard {

    private PanHashGuard() {
    }

    /** True when the value, stripped of spaces/dashes, is 13–19 digits (a raw PAN shape). */
    public static boolean looksLikeRawPan(String value) {
        if (value == null) {
            return false;
        }
        String digits = stripSeparators(value);
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code value} unchanged when it is safe to store as a PAN hash.
     *
     * @param value     candidate pan_hash value (null allowed)
     * @param fieldName name used in the error message (never the value itself)
     * @throws IllegalArgumentException if the value looks like a raw PAN; the message never
     *                                  contains the rejected value
     */
    public static String requireNotRawPan(String value, String fieldName) {
        if (looksLikeRawPan(value)) {
            throw new IllegalArgumentException(fieldName
                    + " must be a hash or token, not a raw card number (rejected a "
                    + stripSeparators(value).length() + "-digit value"
                    + (isLuhnValid(value) ? " that passes the Luhn check" : "") + ")");
        }
        return value;
    }

    /** Luhn (mod 10) check over the digits of the value; false for non-digit input. */
    public static boolean isLuhnValid(String value) {
        if (value == null) {
            return false;
        }
        String digits = stripSeparators(value);
        if (digits.isEmpty()) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int d = c - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Removes the space and dash separators commonly used when writing card numbers. */
    private static String stripSeparators(String value) {
        return value.replace(" ", "").replace("-", "");
    }
}
