package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.dto.compliance.ComplianceLinksResponse;
import com.posgateway.aml.service.compliance.ComplianceLinksQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes indexed cross-links between fraud alerts, cases, SARs, disputes, merchants, and transactions.
 */
@RestController
@RequestMapping("/compliance/links")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_ANALYST')")
public class ComplianceLinksController {

    private final ComplianceLinksQueryService complianceLinksQueryService;

    public ComplianceLinksController(ComplianceLinksQueryService complianceLinksQueryService) {
        this.complianceLinksQueryService = complianceLinksQueryService;
    }

    /** Returns linked compliance records for a merchant subject. */
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ComplianceLinksResponse> merchantLinks(@PathVariable Long merchantId) {
        return ResponseEntity.ok(complianceLinksQueryService.forMerchant(merchantId));
    }

    /** Returns linked compliance records for a card transaction. */
    @GetMapping("/transaction/{txnId}")
    public ResponseEntity<ComplianceLinksResponse> transactionLinks(@PathVariable Long txnId) {
        return ResponseEntity.ok(complianceLinksQueryService.forTransaction(txnId));
    }
}
