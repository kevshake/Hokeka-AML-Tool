package com.posgateway.aml.model.underwriting;

/**
 * Severity of a merchant-verification signal, ordered from least to most serious.
 * Used to weight the underwriting score and to decide whether manual review is forced.
 */
public enum SignalSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
