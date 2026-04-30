package com.posgateway.aml.dto.compliance;

/**
 * Response for {@code POST /compliance/cbk/reports/submit}.
 *
 * <p>Mirrors {@code CbkSubmitResponse} in
 * {@code FRONTEND/src/features/api/cbkReportQueries.ts}:
 * <pre>{ referenceNumber, status, submittedAt, message }</pre>
 *
 * {@code status} uses the FE-facing lowercase enum
 * ({@code "submitted" | "pending" | "failed"}).
 */
public record CbkSubmitResponse(
        String referenceNumber,
        String status,
        String submittedAt,
        String message
) {
}
