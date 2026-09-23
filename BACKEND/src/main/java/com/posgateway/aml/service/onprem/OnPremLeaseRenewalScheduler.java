package com.posgateway.aml.service.onprem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Polls every minute; when {@code nextCheckAt} is due (or lease expired), contacts upstream.
 * Unreachable upstream or failed auth → {@link OnPremServiceGate} STOPPED (fail-closed).
 */
@Component
@ConditionalOnProperty(name = "hokeka.auth.legacy-lease-enabled", havingValue = "true")
public class OnPremLeaseRenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnPremLeaseRenewalScheduler.class);

    private final OnPremServiceGate gate;
    private final OnPremLeaseClientService clientService;

    public OnPremLeaseRenewalScheduler(OnPremServiceGate gate,
                                       OnPremLeaseClientService clientService) {
        this.gate = gate;
        this.clientService = clientService;
    }

    @Scheduled(fixedDelayString = "${hokeka.auth.scheduler-poll-ms:60000}")
    public void poll() {
        OnPremServiceGate.LeaseState state = gate.current();
        if (state.mode() == OnPremServiceGate.Mode.DISABLED) {
            return;
        }
        Instant now = Instant.now();
        boolean leaseExpired = state.validUntil() != null && now.isAfter(state.validUntil());
        boolean checkDue = state.nextCheckAt() != null && !now.isBefore(state.nextCheckAt());
        boolean stopped = state.mode() == OnPremServiceGate.Mode.STOPPED;

        if (!leaseExpired && !checkDue && !stopped) {
            return;
        }

        if (leaseExpired) {
            log.warn("On-prem lease expired at {}; attempting renewal", state.validUntil());
        } else if (checkDue) {
            log.info("On-prem check-in due at {}; contacting upstream", state.nextCheckAt());
        } else {
            log.info("On-prem currently STOPPED; retrying upstream auth");
        }

        boolean ok = clientService.renewFromUpstream();
        if (!ok) {
            log.error("On-prem auth check failed — service remains in STOPPED mode (fail-closed)");
            if (gate.mode() != OnPremServiceGate.Mode.STOPPED) {
                gate.stop("Scheduled renewal failed");
            }
        }
    }
}
