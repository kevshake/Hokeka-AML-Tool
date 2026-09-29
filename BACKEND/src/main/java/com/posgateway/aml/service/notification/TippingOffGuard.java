package com.posgateway.aml.service.notification;

import java.util.regex.Pattern;

/**
 * Narrow tipping-off guard for customer/merchant copy.
 * Internal analyst notifications stay precise.
 */
public final class TippingOffGuard {

    private static final Pattern TIPPING_OFF = Pattern.compile(
            "(?i)(\\bSAR\\b|\\bSTR\\b|suspicious activity|suspicion|investigation|"
                    + "compliance case|money laundering|watchlist|filing a report)");

    private TippingOffGuard() {
    }

    public static boolean containsTippingOffLanguage(String text) {
        return text != null && TIPPING_OFF.matcher(text).find();
    }

    public static String allowInternal(String text) {
        return text;
    }

    public static String sanitizeCustomerText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (!containsTippingOffLanguage(text)) {
            return text;
        }
        return "Your account is being reviewed. No further action is required unless we contact you.";
    }
}
