package com.posgateway.aml.controller.chargeback;

import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import com.posgateway.aml.service.chargeback.VerifiRdrWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visa/Verifi Rapid Dispute Resolution (RDR) webhook endpoint.
 * <p>
 * Register {@code POST /api/v1/integrations/verifi/rdr} with Verifi or your PSP partner.
 * Authenticated via HMAC signature or {@code X-Api-Key} header (see {@code verifi.rdr.*}).
 */
@RestController
@RequestMapping("/integrations/verifi/rdr")
public class VerifiRdrController {

    private static final Logger log = LoggerFactory.getLogger(VerifiRdrController.class);

    private final VerifiRdrWebhookService webhookService;

    public VerifiRdrController(VerifiRdrWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveRdrWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload) {

        if (!webhookService.isAuthenticated(headers, payload)) {
            log.warn("Verifi RDR webhook rejected: invalid signature or API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "rejected", "reason", "invalid_signature"));
        }

        try {
            ChargebackDispute dispute = webhookService.processWebhook(headers, payload);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "accepted");
            body.put("disputeId", dispute.getId());
            body.put("notificationType", dispute.getNotificationType());
            body.put("rdrStatus", dispute.getRdrStatus());
            body.put("alertId", dispute.getAlertId());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Failed to process Verifi RDR webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "processing_failed"));
        }
    }

    /** Health probe for Verifi partner connectivity checks. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "integration", "verifi-rdr"));
    }
}
