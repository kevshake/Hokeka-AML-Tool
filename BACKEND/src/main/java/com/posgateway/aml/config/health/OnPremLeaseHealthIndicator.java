package com.posgateway.aml.config.health;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.service.onprem.OnPremServiceGate;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reflects on-prem lease status on {@code /actuator/health}.
 * When auth is disabled (central cloud), reports UP with detail {@code mode=DISABLED}.
 * When enabled and not RUNNING with a valid lease window, reports DOWN.
 */
@Component("onPremLease")
public class OnPremLeaseHealthIndicator implements HealthIndicator {

    private final HokekaAuthProperties properties;
    private final OnPremServiceGate gate;

    public OnPremLeaseHealthIndicator(HokekaAuthProperties properties, OnPremServiceGate gate) {
        this.properties = properties;
        this.gate = gate;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up()
                    .withDetail("mode", OnPremServiceGate.Mode.DISABLED.name())
                    .withDetail("authEnabled", false)
                    .build();
        }
        OnPremServiceGate.LeaseState state = gate.current();
        if (gate.isOperationsAllowed()) {
            return Health.up()
                    .withDetail("mode", state.mode().name())
                    .withDetail("instanceId", state.instanceId())
                    .withDetail("pspId", state.pspId())
                    .withDetail("validUntil", state.validUntil())
                    .withDetail("nextCheckAt", state.nextCheckAt())
                    .build();
        }
        return Health.down()
                .withDetail("mode", state.mode().name())
                .withDetail("instanceId", state.instanceId())
                .withDetail("validUntil", state.validUntil())
                .withDetail("nextCheckAt", state.nextCheckAt())
                .withDetail("reason", state.stopReason() != null
                        ? state.stopReason()
                        : "Lease missing, expired, or check-in overdue")
                .build();
    }
}
