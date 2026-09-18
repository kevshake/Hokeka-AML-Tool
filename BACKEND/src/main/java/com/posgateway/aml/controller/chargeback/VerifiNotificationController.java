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
 * Verifi API 3.0 Notifications endpoint — {@code POST /notifications}.
 * Verifi appends {@code /notifications} to the merchant base URL configured in the Verifi portal.
 */
@RestController
@RequestMapping("/integrations/verifi/notifications")
public class VerifiNotificationController {

    private static final Logger log = LoggerFactory.getLogger(VerifiNotificationController.class);

    private final VerifiRdrWebhookService webhookService;
    private final VerifiRdrProperties properties;
    private final VerifiRequestAuthenticator authenticator;

    public VerifiNotificationController(VerifiRdrWebhookService webhookService,
                                        VerifiRdrProperties properties,
                                        VerifiRequestAuthenticator authenticator) {
        this.webhookService = webhookService;
        this.properties = properties;
        this.authenticator = authenticator;
    }

    @PostMapping
    public ResponseEntity<Void> receiveNotification(
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload) {

        if (!authenticator.isApiVersionSupported(headers)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (!authenticator.isAuthenticated(headers, payload, properties)) {
            log.warn("Verifi notification rejected: invalid JWS signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            webhookService.processWebhook(headers, payload);
            // Spec: HTTP 200 with empty body (Content-Length: 0)
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process Verifi notification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
