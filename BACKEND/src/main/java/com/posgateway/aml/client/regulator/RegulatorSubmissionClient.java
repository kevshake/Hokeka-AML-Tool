package com.posgateway.aml.client.regulator;

import com.posgateway.aml.entity.reporting.RegulatorySubmission;

/**
 * Common contract for all regulator-side submission adapters.
 *
 * <p>Implementations are real HTTP clients — never mocks. When the regulator
 * credentials / endpoint are not configured, the client MUST throw
 * {@link RegulatorSubmissionDisabledException} rather than fabricating a
 * submission ID. The orchestrating service is responsible for parking the
 * submission in a pending domain status until the regulator is enabled.
 *
 * <p>The argument is the platform's existing {@link RegulatorySubmission}
 * entity (which is the project's analogue of a SAR — it carries the report
 * payload, filing period, jurisdiction, and PSP context). The spec referred
 * to it as {@code SuspiciousActivityReport}; we use the concrete domain type
 * already in the codebase.
 */
public interface RegulatorSubmissionClient {

    /**
     * Submit a SAR / regulatory filing to the upstream regulator.
     *
     * @param submission the prepared regulatory submission (must be APPROVED upstream)
     * @return result carrying the regulator-issued reference and status
     * @throws RegulatorSubmissionDisabledException when this client is configured
     *         but its endpoint / credentials are not yet provisioned
     */
    SubmissionResult submit(RegulatorySubmission submission) throws RegulatorSubmissionDisabledException;

    /** Stable identifier for the regulator (FINCEN / FCA / OFAC). */
    String regulator();
}
