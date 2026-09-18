package com.posgateway.aml.controller.chargeback;

import com.posgateway.aml.integration.verifi.VerifiRdrProperties;
import com.posgateway.aml.integration.verifi.VerifiRequestAuthenticator;
import com.posgateway.aml.service.chargeback.VerifiRdrWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Legacy alias for Verifi notification ingestion.
 * Official Verifi API 3.0 path is {@link VerifiNotificationController} at {@code /notifications}.
 */
@RestController
@RequestMapping({"/integrations/verifi/rdr", "/chargeback/verifi/rdr"})
public class VerifiRdrController {

    private static final Logger log = LoggerFactory.getLogger(VerifiRdrController.class);

    private final VerifiRdrWebhookService webhookService;
    private final VerifiRdrProperties properties;
    private final VerifiRequestAuthenticator authenticator;

    public VerifiRdrController(VerifiRdrWebhookService webhookService,
                               VerifiRdrProperties properties,
                               VerifiRequestAuthenticator authenticator) {
        this.webhookService = webhookService;
        this.properties = properties;
        this.authenticator = authenticator;
    }

    @PostMapping
    public ResponseEntity<Void> receiveRdrWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload) {

        if (!authenticator.isApiVersionSupported(headers)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (!authenticator.isAuthenticated(headers, payload, properties)) {
            log.warn("Verifi RDR webhook rejected: invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            webhookService.processWebhook(headers, payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process Verifi RDR webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "integration", "verifi-rdr"));
    }
}
