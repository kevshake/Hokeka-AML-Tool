package com.posgateway.aml.client.regulator;

/**
 * Thrown when a regulator client is wired into the application but its
 * {@code regulators.<name>.enabled} flag (or its endpoint) is unset.
 *
 * <p>Checked on purpose: this is a known configuration gap, not an
 * unexpected runtime error. The submission service catches it and parks
 * the SAR in a pending state so it can be re-driven once the regulator
 * credentials arrive — we never fall back to fabricated submission IDs.
 */
public class RegulatorSubmissionDisabledException extends Exception {

    private final String regulator;

    public RegulatorSubmissionDisabledException(String regulator, String message) {
        super(message);
        this.regulator = regulator;
    }

    public String getRegulator() {
        return regulator;
    }
}
