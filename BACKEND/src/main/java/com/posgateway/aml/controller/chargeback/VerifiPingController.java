package com.posgateway.aml.controller.chargeback;

import com.posgateway.aml.integration.verifi.VerifiRdrProperties;
import com.posgateway.aml.integration.verifi.VerifiRequestAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifi API 3.0 Ping endpoint — {@code POST /ping}.
 */
@RestController
@RequestMapping("/integrations/verifi/ping")
public class VerifiPingController {

    private static final Logger log = LoggerFactory.getLogger(VerifiPingController.class);

    private final VerifiRdrProperties properties;
    private final VerifiRequestAuthenticator authenticator;

    public VerifiPingController(VerifiRdrProperties properties,
                                VerifiRequestAuthenticator authenticator) {
        this.properties = properties;
        this.authenticator = authenticator;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ping(
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) Map<String, Object> payload) {

        if (!authenticator.isApiVersionSupported(headers)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (!authenticator.isAuthenticated(headers, payload != null ? payload : Map.of(), properties)) {
            log.warn("Verifi ping rejected: invalid JWS signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> verifiEntityInfo = new LinkedHashMap<>();
        if (properties.getPartnerId() != null) {
            verifiEntityInfo.put("partnerId", properties.getPartnerId());
        }
        if (properties.getClientId() != null) {
            verifiEntityInfo.put("clientId", properties.getClientId());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verifiEntityInfo", verifiEntityInfo);
        return ResponseEntity.ok(body);
    }
}
