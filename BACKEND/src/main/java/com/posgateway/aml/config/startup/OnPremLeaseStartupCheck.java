package com.posgateway.aml.config.startup;

import com.posgateway.aml.config.onprem.OnPremLeaseDeprecation;
import com.posgateway.aml.service.onprem.OnPremServiceGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Detects a JVM still configured for the removed full-BACKEND on-prem lease path and fail-closes
 * with loud migration guidance. Edge Node is the supported on-premises deployment.
 */
@Component
@Order(50)
@ConditionalOnProperty(name = "hokeka.auth.enabled", havingValue = "true")
public class OnPremLeaseStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OnPremLeaseStartupCheck.class);

    private final OnPremServiceGate gate;

    public OnPremLeaseStartupCheck(OnPremServiceGate gate) {
        this.gate = gate;
    }

    @Override
    public void run(ApplicationArguments args) {
        gate.stop(OnPremLeaseDeprecation.MESSAGE);
        log.error("====================================================================");
        log.error(" ON-PREM FULL-BACKEND LEASE MODE REMOVED ({})", OnPremLeaseDeprecation.CODE);
        log.error(" {}", OnPremLeaseDeprecation.MESSAGE);
        log.error(" Install an Edge Node: docs/edge-client-install-guide.md");
        log.error(" Dual-post contract: docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md");
        log.error("====================================================================");
    }
}
