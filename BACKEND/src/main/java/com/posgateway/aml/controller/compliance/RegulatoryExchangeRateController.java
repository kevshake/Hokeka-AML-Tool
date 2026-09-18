package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.entity.billing.CurrencyRate;
import com.posgateway.aml.service.compliance.RegulatoryExchangeRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/compliance/exchange-rates")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER')")
public class RegulatoryExchangeRateController {

    private final RegulatoryExchangeRateService service;

    public RegulatoryExchangeRateController(RegulatoryExchangeRateService service) {
        this.service = service;
    }

    @GetMapping
    public List<CurrencyRate> list() {
        return service.listRates();
    }

    @PutMapping("/{currency}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public CurrencyRate approve(@PathVariable String currency,
                                @RequestBody RateApprovalRequest request,
                                Authentication authentication) {
        return service.approveRate(
                currency,
                request.rateToUsd(),
                request.source(),
                request.effectiveAt(),
                request.expiresAt(),
                actor(authentication));
    }

    @PostMapping("/{currency}/revoke")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<CurrencyRate> revoke(@PathVariable String currency,
                                               Authentication authentication) {
        return ResponseEntity.ok(service.revokeApproval(currency, actor(authentication)));
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "system";
    }

    public record RateApprovalRequest(
            BigDecimal rateToUsd,
            String source,
            LocalDateTime effectiveAt,
            LocalDateTime expiresAt) {
    }
}
