package com.posgateway.aml.service.onprem;

import com.posgateway.aml.client.onprem.HokekaUpstreamAuthClient;
import com.posgateway.aml.client.onprem.HokekaUpstreamAuthClient.UpstreamAuthException;
import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * On-prem orchestration: load/verify persisted lease, renew with upstream, fail-closed on error.
 */
@Service
@ConditionalOnProperty(name = "hokeka.auth.enabled", havingValue = "true")
public class OnPremLeaseClientService {

    private static final Logger log = LoggerFactory.getLogger(OnPremLeaseClientService.class);

    private final HokekaAuthProperties properties;
    private final HokekaUpstreamAuthClient upstreamClient;
    private final OnPremLeaseStore leaseStore;
    private final OnPremLeaseTokenService tokenService;
    private final OnPremServiceGate gate;

    public OnPremLeaseClientService(HokekaAuthProperties properties,
                                    HokekaUpstreamAuthClient upstreamClient,
                                    OnPremLeaseStore leaseStore,
                                    OnPremLeaseTokenService tokenService,
                                    OnPremServiceGate gate) {
        this.properties = properties;
        this.upstreamClient = upstreamClient;
        this.leaseStore = leaseStore;
        this.tokenService = tokenService;
        this.gate = gate;
    }

    /**
     * Startup / scheduler entry: try persisted lease, else call upstream. On any failure → STOPPED.
     *
     * @return true if RUNNING after this call
     */
    public boolean ensureAuthorized(boolean forceUpstream) {
        Instant now = Instant.now();
        if (!forceUpstream) {
            if (tryRestorePersistedLease(now)) {
                // Still need upstream if check-in is due.
                OnPremServiceGate.LeaseState state = gate.current();
                if (state.nextCheckAt() != null && !now.isBefore(state.nextCheckAt())) {
                    log.info("On-prem lease check-in due at {}; contacting upstream", state.nextCheckAt());
                    return renewFromUpstream();
                }
                log.info("On-prem lease restored from store; validUntil={} nextCheckAt={}",
                        state.validUntil(), state.nextCheckAt());
                return true;
            }
        }
        return renewFromUpstream();
    }

    public boolean renewFromUpstream() {
        try {
            OnPremLeaseResponse lease = upstreamClient.requestLease();
            if (tokenService.hasSigningSecret()) {
                OnPremLeaseTokenService.LeaseClaims claims = tokenService.verify(lease.leaseToken());
                if (!claims.instanceId().equals(properties.getInstanceId())) {
                    gate.stop("Lease token instanceId mismatch");
                    return false;
                }
            }
            gate.applyLease(lease);
            leaseStore.save(lease);
            log.info("On-prem lease renewed; validUntil={} nextCheckAt={}",
                    lease.validUntil(), lease.nextCheckAt());
            return true;
        } catch (UpstreamAuthException | IllegalArgumentException | IllegalStateException e) {
            log.error("On-prem lease renewal failed — entering STOPPED mode: {}", e.getMessage());
            gate.stop(e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.error("On-prem lease renewal failed unexpectedly — STOPPED: {}", e.getMessage());
            gate.stop("Unexpected lease failure: " + e.getMessage());
            return false;
        }
    }

    private boolean tryRestorePersistedLease(Instant now) {
        OnPremLeaseStore.StoredLease stored = leaseStore.load();
        if (stored == null || stored.leaseToken() == null) {
            return false;
        }
        if (stored.validUntil() == null || now.isAfter(stored.validUntil())) {
            log.warn("Persisted lease expired at {}", stored.validUntil());
            return false;
        }
        if (tokenService.hasSigningSecret()) {
            try {
                OnPremLeaseTokenService.LeaseClaims claims = tokenService.verify(stored.leaseToken());
                if (!claims.instanceId().equals(properties.getInstanceId())) {
                    return false;
                }
                if (now.isAfter(claims.validUntil())) {
                    return false;
                }
                gate.applyPersisted(claims.instanceId(), claims.pspId(), claims.validUntil(),
                        claims.nextCheckAt(), stored.leaseToken());
                return true;
            } catch (RuntimeException e) {
                log.warn("Persisted lease token failed verification: {}", e.getMessage());
                return false;
            }
        }
        // No local verify secret: trust metadata only until nextCheckAt / validUntil.
        gate.applyPersisted(stored.instanceId(), stored.pspId(), stored.validUntil(),
                stored.nextCheckAt(), stored.leaseToken());
        return true;
    }
}
