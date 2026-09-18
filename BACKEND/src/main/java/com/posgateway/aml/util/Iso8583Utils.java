package com.posgateway.aml.util;

import java.util.Set;

/**
 * ISO 8583 Utility
 * Mapping DE39 Response Codes to Risk Signals
 */
public class Iso8583Utils {

    // 1. High-confidence stolen / lost card indicators
    private static final Set<String> LOST_STOLEN_CODES = Set.of("41", "43");

    // 2. Strong fraud-risk indicators (Restricted, Not Permitted)
    // 05: Do Not Honor (often generic decline but can mask fraud)
    // 57: Txn not permitted to cardholder
    // 62: Restricted card
    private static final Set<String> FRAUD_RISK_CODES = Set.of("05", "57", "62");

    // 3. Velocity / Testing indicators (Insufficient funds, Limits)
    // 51: Insufficient funds
    // 61: Exceeds amount limit
    // 65: Exceeds frequency limit
    private static final Set<String> VELOCITY_CODES = Set.of("51", "61", "65");

    // 4. Chargeback Codes (Simplified mapping - usually these come from chargeback
    // feeds, not auth response)
    // But if we get a specific decline that implies previous chargeback, strict
    // schemes use specific codes.
    // For this context, we focus on Auth Response.

    public enum FraudSignal {
        CONFIRMED_FRAUD, // Lost/Stolen
        HIGH_RISK, // Restricted
        VELOCITY, // Limits
        NONE // Normal/Technical
    }

    /**
     * Map DE39 code to Fraud Signal
     */
    public static FraudSignal getFraudSignal(String responseCode) {
        if (responseCode == null)
            return FraudSignal.NONE;

        if (LOST_STOLEN_CODES.contains(responseCode)) {
            return FraudSignal.CONFIRMED_FRAUD;
        }
        if (FRAUD_RISK_CODES.contains(responseCode)) {
            return FraudSignal.HIGH_RISK;
        }
        if (VELOCITY_CODES.contains(responseCode)) {
            return FraudSignal.VELOCITY;
        }
        return FraudSignal.NONE;
    }

    public static boolean isFraud(String responseCode) {
        return LOST_STOLEN_CODES.contains(responseCode);
    }
}
