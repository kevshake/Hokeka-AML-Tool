package com.posgateway.aml.dto.compliance;

import java.util.List;

/**
 * Aggregated cross-links for investigations (alerts, cases, SARs, disputes, txns, risk rows).
 */
public record ComplianceLinksResponse(
        String anchorType,
        String anchorId,
        List<LinkedRecord> alerts,
        List<LinkedRecord> cases,
        List<LinkedRecord> transactions,
        List<LinkedRecord> sars,
        List<LinkedRecord> disputes,
        List<LinkedRecord> monitoringAlerts,
        List<LinkedRecord> riskProfiles,
        List<LinkedRecord> screeningActivities
) {
    /** Lightweight pointer to another persisted record in the compliance graph. */
    public record LinkedRecord(String recordType, String recordId, String relationship, String label) {
    }
}
