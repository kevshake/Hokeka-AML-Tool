package com.posgateway.aml.service.onprem;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory fail-closed gate for on-prem deployments.
 * When {@link Mode#STOPPED}, ingest and scoring must refuse work.
 */
@Component
public class OnPremServiceGate {

    public enum Mode {
        /** Auth not required (central / cloud deployment). */
        DISABLED,
        /** Lease valid; operations permitted. */
        RUNNING,
        /** Lease missing, expired, revoked, or upstream unreachable at check time. */
        STOPPED
    }

    public record LeaseState(
            Mode mode,
            String instanceId,
            Long pspId,
            Instant validUntil,
            Instant nextCheckAt,
            String leaseToken,
            String stopReason,
            Instant updatedAt
    ) {
    }

    private final AtomicReference<LeaseState> state;

    public OnPremServiceGate(HokekaAuthProperties properties) {
        // Fail-closed from the first request: on-prem starts STOPPED until a lease is proven.
        if (properties.isEnabled()) {
            this.state = new AtomicReference<>(new LeaseState(
                    Mode.STOPPED, null, null, null, null, null,
                    "awaiting startup authorization", Instant.now()));
        } else {
            this.state = new AtomicReference<>(new LeaseState(
                    Mode.DISABLED, null, null, null, null, null, null, Instant.now()));
        }
    }

    public LeaseState current() {
        return state.get();
    }

    public Mode mode() {
        return state.get().mode();
    }

    public boolean isOperationsAllowed() {
        Mode mode = mode();
        if (mode == Mode.DISABLED) {
            return true;
        }
        if (mode != Mode.RUNNING) {
            return false;
        }
        LeaseState s = state.get();
        Instant now = Instant.now();
        if (s.validUntil() != null && now.isAfter(s.validUntil())) {
            return false;
        }
        // Overdue check-in without successful renewal → fail-closed.
        if (s.nextCheckAt() != null && now.isAfter(s.nextCheckAt())) {
            return false;
        }
        return true;
    }

    public void markDisabled() {
        state.set(new LeaseState(Mode.DISABLED, null, null, null, null, null, null, Instant.now()));
    }

    public void applyLease(OnPremLeaseResponse lease) {
        state.set(new LeaseState(
                Mode.RUNNING,
                lease.instanceId(),
                lease.pspId(),
                lease.validUntil(),
                lease.nextCheckAt(),
                lease.leaseToken(),
                null,
                Instant.now()));
    }

    public void applyPersisted(String instanceId, Long pspId, Instant validUntil,
                               Instant nextCheckAt, String leaseToken) {
        state.set(new LeaseState(
                Mode.RUNNING,
                instanceId,
                pspId,
                validUntil,
                nextCheckAt,
                leaseToken,
                null,
                Instant.now()));
    }

    public void stop(String reason) {
        LeaseState prev = state.get();
        state.set(new LeaseState(
                Mode.STOPPED,
                prev.instanceId(),
                prev.pspId(),
                prev.validUntil(),
                prev.nextCheckAt(),
                prev.leaseToken(),
                reason,
                Instant.now()));
    }
}
