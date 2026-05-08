package com.posgateway.aml.client.regulator;

import java.time.Instant;

/**
 * Outcome of a real outbound submission to a regulator.
 *
 * @param submissionId the regulator-issued reference (e.g. BSA submission ID)
 * @param status       short status string from the regulator response
 *                     ("ACCEPTED", "QUEUED", "NO_OP" for read-only adapters, etc.)
 * @param submittedAt  when the call to the regulator returned success
 * @param regulator    the regulator code (FINCEN/FCA/OFAC) — useful for downstream logging
 */
public record SubmissionResult(
        String submissionId,
        String status,
        Instant submittedAt,
        String regulator
) {
}
