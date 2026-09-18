package com.posgateway.aml.entity.psp;

/** Controls whether taxonomy signals decide, merely report, or route an alert. */
public enum SignalTaxonomyMode {
    REPORTING_ONLY,
    INFLUENCE_DECISION,
    ALERT_ROUTING
}
