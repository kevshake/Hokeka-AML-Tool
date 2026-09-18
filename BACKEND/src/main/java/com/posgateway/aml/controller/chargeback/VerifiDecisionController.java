package com.posgateway.aml.controller.chargeback;

import com.posgateway.aml.integration.verifi.VerifiRdrProperties;
import com.posgateway.aml.integration.verifi.VerifiRequestAuthenticator;
import com.posgateway.aml.service.chargeback.VerifiDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Verifi API 3.0 Decisions endpoint — {@code POST /decisions}.
 * Verifi appends {@code /decisions} to the merchant base URL configured in the Verifi portal.
 */
@RestController
@RequestMapping("/integrations/verifi")
public class VerifiDecisionController {

    private static final Logger log = LoggerFactory.getLogger(VerifiDecisionController.class);

    private final VerifiDecisionService decisionService;
    private final VerifiRdrProperties properties;
    private final VerifiRequestAuthenticator authenticator;

    public VerifiDecisionController(VerifiDecisionService decisionService,
                                    VerifiRdrProperties properties,
                                    VerifiRequestAuthenticator authenticator) {
        this.decisionService = decisionService;
        this.properties = properties;
        this.authenticator = authenticator;
    }

    @PostMapping({"/decisions", "/decision"})
    public ResponseEntity<Map<String, Object>> decide(
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload) {

        if (!authenticator.isApiVersionSupported(headers)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (!authenticator.isAuthenticated(headers, payload, properties)) {
            log.warn("Verifi decision request rejected: invalid JWS signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            return ResponseEntity.ok(decisionService.evaluateDecision(payload));
        } catch (Exception e) {
            log.error("Verifi decision processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
