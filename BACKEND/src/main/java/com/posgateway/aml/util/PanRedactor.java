package com.posgateway.aml.util;

import com.posgateway.aml.edge.tunnel.LuhnGuard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts PAN-like values from analyst free text (residency contract §2.3, plan conflict 7):
 * a narrative is workflow data, and any run of 13..19 digits (optionally separated by single spaces
 * or hyphens) that passes the §2.5 Luhn guard is replaced before the text is stored or filed.
 */
public final class PanRedactor {

    /** Replacement for a PAN-like value. */
    public static final String REDACTED = "[REDACTED-PAN]";

    /** Digit runs of 13..19 digits, optionally separated by single spaces or hyphens. */
    private static final Pattern DIGIT_RUN = Pattern.compile("(?<![0-9])[0-9](?:[ -]?[0-9]){12,18}(?![0-9])");

    /** Utility class. */
    private PanRedactor() {
    }

    /** The text with every PAN-like value replaced by {@link #REDACTED}; null stays null. */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = DIGIT_RUN.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        boolean changed = false;
        while (m.find()) {
            boolean pan = LuhnGuard.isPanLike(m.group());
            changed |= pan;
            m.appendReplacement(out, pan ? REDACTED : Matcher.quoteReplacement(m.group()));
        }
        if (!changed) {
            return text;
        }
        m.appendTail(out);
        return out.toString();
    }

    /** True when the text holds a PAN-like value (the Console warning of §2.3). */
    public static boolean containsPan(String text) {
        return text != null && !text.equals(redact(text));
    }
}
