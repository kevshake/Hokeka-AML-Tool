package com.posgateway.aml.model.underwriting;

import java.time.Instant;

/**
 * A single normalized merchant-verification signal, produced by an internal check or an
 * external provider adapter. Every vendor/internal result is flattened into this common
 * structure so the orchestrator, decision engine and case store speak one vocabulary.
 *
 * <p>Mirrors the normalized signal contract from the KYB research brief:
 * {@code signalCode, severity, source, observedAt, confidence, entityId,
 * evidenceReference, requiresManualReview}.
 */
public final class VerificationSignal {

    private final String signalCode;
    private final SignalSeverity severity;
    private final String source;
    private final Instant observedAt;
    private final double confidence;
    private final String entityRef;
    private final String evidenceReference;
    private final boolean requiresManualReview;
    private final String detail;

    public VerificationSignal(String signalCode, SignalSeverity severity, String source, Instant observedAt,
                              double confidence, String entityRef, String evidenceReference,
                              boolean requiresManualReview, String detail) {
        this.signalCode = signalCode;
        this.severity = severity;
        this.source = source;
        this.observedAt = observedAt;
        this.confidence = confidence;
        this.entityRef = entityRef;
        this.evidenceReference = evidenceReference;
        this.requiresManualReview = requiresManualReview;
        this.detail = detail;
    }

    public static VerificationSignal of(String signalCode, SignalSeverity severity, String source,
                                        String entityRef, boolean requiresManualReview, String detail) {
        return new VerificationSignal(signalCode, severity, source, Instant.now(), 1.0, entityRef,
                null, requiresManualReview, detail);
    }

    public String getSignalCode() { return signalCode; }
    public SignalSeverity getSeverity() { return severity; }
    public String getSource() { return source; }
    public Instant getObservedAt() { return observedAt; }
    public double getConfidence() { return confidence; }
    public String getEntityRef() { return entityRef; }
    public String getEvidenceReference() { return evidenceReference; }
    public boolean isRequiresManualReview() { return requiresManualReview; }
    public String getDetail() { return detail; }
}
