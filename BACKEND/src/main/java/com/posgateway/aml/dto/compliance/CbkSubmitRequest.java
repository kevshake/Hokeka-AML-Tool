package com.posgateway.aml.dto.compliance;

import java.util.Map;

/**
 * Body of {@code POST /compliance/cbk/reports/submit}.
 *
 * <p>Mirrors {@code CbkSubmitRequest} in
 * {@code FRONTEND/src/features/api/cbkReportQueries.ts}:
 * <pre>{ endpointType, period, from, to, parameters? }</pre>
 *
 * <p>{@code reportId} is retained as a temporary compatibility alias. New
 * callers must send an exact {@code CbkEndpointType} name.
 */
public record CbkSubmitRequest(
        String endpointType,
        String reportId,
        String period,
        String from,
        String to,
        Map<String, Object> parameters
) {
    public String requestedEndpoint() {
        return endpointType != null && !endpointType.isBlank() ? endpointType : reportId;
    }
}
