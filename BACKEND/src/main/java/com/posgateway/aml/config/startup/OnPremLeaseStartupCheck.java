package com.posgateway.aml.config.startup;

import com.posgateway.aml.service.onprem.OnPremLeaseClientService;
import com.posgateway.aml.service.onprem.OnPremServiceGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On-prem startup: restore or obtain a lease. Failure leaves the gate STOPPED so ingest/scoring
 * refuse work (JVM still boots for health/diagnostics).
 */
@Component
@Order(50)
@ConditionalOnProperty(name = "hokeka.auth.enabled", havingValue = "true")
public class OnPremLeaseStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OnPremLeaseStartupCheck.class);

    private final OnPremLeaseClientService clientService;
    private final OnPremServiceGate gate;

    public OnPremLeaseStartupCheck(OnPremLeaseClientService clientService, OnPremServiceGate gate) {
        this.clientService = clientService;
        this.gate = gate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("On-prem Hokeka auth enabled — validating service lease");
        boolean ok = clientService.ensureAuthorized(false);
        if (!ok) {
            gate.stop(gate.current().stopReason() != null
                    ? gate.current().stopReason()
                    : "No valid lease at startup");
            log.error("====================================================================");
            log.error(" ON-PREM SERVICE AUTHORIZATION FAILED — OPERATIONS STOPPED");
            log.error(" Transaction ingest and scoring are blocked until a lease is obtained");
            log.error(" from Hokeka central (hokeka.auth.upstream-url).");
            log.error("====================================================================");
        } else {
            log.info("On-prem service authorization OK — mode={}", gate.mode());
        }
    }
}
