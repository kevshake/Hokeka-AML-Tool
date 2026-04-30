package com.posgateway.aml.controller;

import com.posgateway.aml.repository.AerospikeMetricsRepository;
import com.posgateway.aml.util.Iso8583Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Transaction Result Controller
 * Receives feedback (authorization results, chargebacks) from the gateway
 * Updates fraud metrics (Async)
 */
@RestController
@RequestMapping("/transaction/result")
public class TransactionResultController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionResultController.class);

    private final AerospikeMetricsRepository metricsRepository;

    @Autowired
    public TransactionResultController(AerospikeMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    /**
     * Ingest Transaction Result (Authorization Response)
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<String>> ingestResult(@RequestBody Map<String, Object> resultPayload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String merchantId = (String) resultPayload.get("merchantId");
                String responseCode = (String) resultPayload.get("responseCode"); // ISO 8583 DE39
                Number amountObj = (Number) resultPayload.get("amountCents");
                long amountCents = amountObj != null ? amountObj.longValue() : 0;

                Boolean isChargeback = (Boolean) resultPayload.getOrDefault("isChargeback", false);

                if (merchantId == null) {
                    return ResponseEntity.badRequest().body("Missing merchantId");
                }

                // Determine if this is a confirmed fraud signal from response code
                boolean isFraud = false;
                if (!Boolean.TRUE.equals(isChargeback)) {
                    Iso8583Utils.FraudSignal signal = Iso8583Utils.getFraudSignal(responseCode);
                    if (signal == Iso8583Utils.FraudSignal.CONFIRMED_FRAUD) {
                        isFraud = true;
                        logger.warn("Received CONFIRMED FRAUD signal for merchant {} (Code: {})", merchantId,
                                responseCode);
                    }
                }

                // Update Counters (Fire & Forget)
                metricsRepository.incrementCounters(merchantId, isFraud, Boolean.TRUE.equals(isChargeback),
                        amountCents);

                return ResponseEntity.ok("Result processed");

            } catch (Exception e) {
                logger.error("Error processing transaction result", e);
                return ResponseEntity.internalServerError().body("Error processing result");
            }
        });
    }
}
