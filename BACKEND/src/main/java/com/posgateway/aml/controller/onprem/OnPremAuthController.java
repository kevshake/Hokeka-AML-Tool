package com.posgateway.aml.controller.onprem;

import com.posgateway.aml.dto.onprem.OnPremLeaseRequest;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import com.posgateway.aml.service.onprem.OnPremLeaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-to-machine lease endpoints for on-prem PSP instances.
 * Authenticated solely by client id/secret in the request body (no user session).
 */
@RestController
@RequestMapping("/onprem/auth")
public class OnPremAuthController {

    private final OnPremLeaseService leaseService;

    public OnPremAuthController(OnPremLeaseService leaseService) {
        this.leaseService = leaseService;
    }

    /**
     * Initial authentication / lease grant.
     * POST /api/v1/onprem/auth/lease
     */
    @PostMapping("/lease")
    public ResponseEntity<OnPremLeaseResponse> lease(@Valid @RequestBody OnPremLeaseRequest request) {
        return ResponseEntity.ok(leaseService.authenticateAndGrant(request));
    }

    /**
     * Explicit renewal alias — identical credentials flow; refreshes validUntil + nextCheckAt.
     * POST /api/v1/onprem/auth/renew
     */
    @PostMapping("/renew")
    public ResponseEntity<OnPremLeaseResponse> renew(@Valid @RequestBody OnPremLeaseRequest request) {
        return ResponseEntity.ok(leaseService.authenticateAndGrant(request));
    }

    /**
     * Heartbeat / daily check-in — same as renew (server re-assigns nextCheckAt).
     * POST /api/v1/onprem/auth/heartbeat
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<OnPremLeaseResponse> heartbeat(@Valid @RequestBody OnPremLeaseRequest request) {
        return ResponseEntity.ok(leaseService.authenticateAndGrant(request));
    }
}
