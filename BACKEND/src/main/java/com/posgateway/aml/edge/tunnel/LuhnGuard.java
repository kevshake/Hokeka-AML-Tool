package com.posgateway.aml.edge.tunnel;

/**
 * The §2.5 Luhn guard: a string is <b>PAN-like</b> when, after removing spaces and hyphens, it is
 * 13 to 19 decimal digits and passes the Luhn check. Enforced for every schema field marked
 * {@code x-hokeka-luhnReject} (generic validators ignore the keyword, §13), byte-for-byte the same
 * rule as {@code docs/contracts/validate.py luhn_pan_like}.
 */
public final class LuhnGuard {

    /** Utility class. */
    private LuhnGuard() {
    }

    /** True when {@code value} looks like a card number and must be refused. */
    public static boolean isPanLike(String value) {
        if (value == null) {
            return false;
        }
        StringBuilder digits = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            if (c < '0' || c > '9') {
                return false;
            }
            digits.append(c);
        }
        int len = digits.length();
        if (len < 13 || len > 19) {
            return false;
        }
        int total = 0;
        for (int i = 0; i < len; i++) {
            int d = digits.charAt(len - 1 - i) - '0';
            if (i % 2 == 1) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            total += d;
        }
        return total % 10 == 0;
    }
}
