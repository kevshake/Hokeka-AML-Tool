package com.posgateway.aml.model.underwriting;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalized result returned by an {@code ExternalVerificationProvider} adapter.
 *
 * <p>Status semantics — deliberately fail-closed, matching the platform-wide principle
 * that "unavailable" must never be mistaken for "clear":
 * <ul>
 *   <li>{@link Status#COMPLETED} — the provider actually ran and returned findings.</li>
 *   <li>{@link Status#UNAVAILABLE} — the provider is not configured / not credentialed /
 *       unreachable. It produces NO clearance; the orchestrator forces manual review.</li>
 *   <li>{@link Status#ERROR} — the provider was configured but the call failed.</li>
 * </ul>
 */
public final class ProviderResult {

    public enum Status { COMPLETED, UNAVAILABLE, ERROR }

    private final String provider;
    private final Status status;
    private final List<VerificationSignal> signals;
    private final String providerReference;
    private final String message;

    public ProviderResult(String provider, Status status, List<VerificationSignal> signals,
                          String providerReference, String message) {
        this.provider = provider;
        this.status = status;
        this.signals = signals != null ? signals : new ArrayList<>();
        this.providerReference = providerReference;
        this.message = message;
    }

    /** The provider is not configured/credentialed — no clearance, force manual review. */
    public static ProviderResult unavailable(String provider, String entityRef, String reason) {
        VerificationSignal s = VerificationSignal.of(
                provider.toUpperCase() + "_UNAVAILABLE", SignalSeverity.MEDIUM, provider, entityRef,
                true, "Provider unavailable: " + reason + " — manual review required (no clearance).");
        List<VerificationSignal> list = new ArrayList<>();
        list.add(s);
        return new ProviderResult(provider, Status.UNAVAILABLE, list, null, reason);
    }

    public static ProviderResult completed(String provider, List<VerificationSignal> signals, String ref) {
        return new ProviderResult(provider, Status.COMPLETED, signals, ref, null);
    }

    public static ProviderResult error(String provider, String entityRef, String message) {
        VerificationSignal s = VerificationSignal.of(
                provider.toUpperCase() + "_ERROR", SignalSeverity.MEDIUM, provider, entityRef,
                true, "Provider error: " + message + " — manual review required.");
        List<VerificationSignal> list = new ArrayList<>();
        list.add(s);
        return new ProviderResult(provider, Status.ERROR, list, null, message);
    }

    public String getProvider() { return provider; }
    public Status getStatus() { return status; }
    public List<VerificationSignal> getSignals() { return signals; }
    public String getProviderReference() { return providerReference; }
    public String getMessage() { return message; }
}
