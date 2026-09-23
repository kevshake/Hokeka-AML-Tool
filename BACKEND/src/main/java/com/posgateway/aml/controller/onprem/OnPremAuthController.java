package com.posgateway.aml.controller.onprem;

import com.posgateway.aml.config.onprem.OnPremLeaseDeprecation;
import com.posgateway.aml.dto.onprem.OnPremLeaseRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Former machine-to-machine lease endpoints for full-BACKEND on-prem deployments.
 *
 * <p><b>Product path removed.</b> PSPs deploy an {@code Edge Node} instead. These endpoints fail
 * closed with {@code 410 Gone} so legacy installers cannot silently obtain a lease.
 */
@RestController
@RequestMapping("/onprem/auth")
public class OnPremAuthController {

    @PostMapping("/lease")
    public ResponseEntity<?> lease(@Valid @RequestBody OnPremLeaseRequest request) {
        return OnPremLeaseDeprecation.gone();
    }

    @PostMapping("/renew")
    public ResponseEntity<?> renew(@Valid @RequestBody OnPremLeaseRequest request) {
        return OnPremLeaseDeprecation.gone();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@Valid @RequestBody OnPremLeaseRequest request) {
        return OnPremLeaseDeprecation.gone();
    }
}
