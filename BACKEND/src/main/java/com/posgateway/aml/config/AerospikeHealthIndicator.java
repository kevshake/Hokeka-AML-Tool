package com.posgateway.aml.config;

import com.aerospike.client.AerospikeClient;
import com.posgateway.aml.service.AerospikeConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes Aerospike connectivity status in Spring Boot Actuator's
 * /actuator/health endpoint as the "aerospike" component.
 */
@Component("aerospike")
public class AerospikeHealthIndicator implements HealthIndicator {

    private final AerospikeConnectionService connectionService;

    @Autowired
    public AerospikeHealthIndicator(AerospikeConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Override
    public Health health() {
        if (!connectionService.isEnabled()) {
            return Health.unknown()
                    .withDetail("status", "DISABLED")
                    .withDetail("message", "Aerospike is disabled via aerospike.enabled=false")
                    .build();
        }

        try {
            AerospikeClient client = connectionService.getClient();
            if (client != null && client.isConnected()) {
                int nodeCount = client.getNodes().length;
                return Health.up()
                        .withDetail("status", "CONNECTED")
                        .withDetail("nodes", nodeCount)
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "DISCONNECTED")
                        .withDetail("message", "Aerospike client is not connected")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "ERROR")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
