package com.posgateway.aml.exception;

/**
 * Thrown when {@code SarContentGenerationService} is asked for a regulator
 * + jurisdiction combination that has no active row in {@code sar_templates}.
 *
 * <p>Surfaces a 5xx via {@code GlobalExceptionHandler} (RuntimeException
 * catch-all) — operators are expected to seed the template before SARs can
 * be filed for that jurisdiction.
 */
public class SarTemplateNotConfiguredException extends RuntimeException {

    public SarTemplateNotConfiguredException(String regulator, String jurisdiction) {
        super("No active SAR template configured for regulator='" + regulator
                + "' jurisdiction='" + jurisdiction + "'. Seed sar_templates before filing.");
    }
}
