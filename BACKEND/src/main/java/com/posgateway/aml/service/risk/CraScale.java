package com.posgateway.aml.service.risk;

/**
 * Normalizes customer risk assessment (CRA) values to the single platform scale 0–100.
 */
public final class CraScale {

    private CraScale() {
    }

    /**
     * Converts legacy 0–1 stored CRA values to 0–100; leaves values already on the 0–100 scale unchanged.
     */
    public static double normalizeToHundred(Double cra) {
        if (cra == null) {
            return 0.0;
        }
        if (cra > 0 && cra <= 1.0) {
            return cra * 100.0;
        }
        return cra;
    }

    /**
     * Clamps a CRA score into the inclusive 0–100 range used by merchants and transactions.
     */
    public static double clampHundred(double cra) {
        return Math.max(0.0, Math.min(100.0, cra));
    }
}
