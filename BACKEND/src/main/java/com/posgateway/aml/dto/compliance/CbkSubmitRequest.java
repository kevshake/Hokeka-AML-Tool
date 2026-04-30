package com.posgateway.aml.dto.compliance;

import java.util.Map;

/**
 * Body of {@code POST /compliance/cbk/reports/submit}.
 *
 * <p>Mirrors {@code CbkSubmitRequest} in
 * {@code FRONTEND/src/features/api/cbkReportQueries.ts}:
 * <pre>{ reportId, period, from, to, parameters? }</pre>
 *
 * <p>{@code reportId} is the FE's identifier for the report template
 * (e.g. "cbk-returns") and is stored as the entity's {@code reportType}.
 */
public record CbkSubmitRequest(
        String reportId,
        String period,
        String from,
        String to,
        Map<String, Object> parameters
) {
}
