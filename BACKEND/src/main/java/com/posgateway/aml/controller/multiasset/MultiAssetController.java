package com.posgateway.aml.controller.multiasset;

import com.posgateway.aml.dto.multiasset.MultiAssetDtos.*;
import com.posgateway.aml.service.multiasset.MultiAssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/multi-asset")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_USER')")
public class MultiAssetController {

    private final MultiAssetService multiAssetService;

    public MultiAssetController(MultiAssetService multiAssetService) {
        this.multiAssetService = multiAssetService;
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<CustomerSummary> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(multiAssetService.createCustomer(request));
    }

    @GetMapping("/customers")
    public Page<CustomerSummary> listCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return multiAssetService.listCustomers(search, page, size);
    }

    @PostMapping("/customers/{customerId}/accounts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<AccountSummary> createAccount(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateAssetAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(multiAssetService.createAccount(customerId, request));
    }

    @PostMapping("/customers/{customerId}/relationships")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<CustomerRelationshipResponse> createRelationship(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateCustomerRelationshipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(multiAssetService.createRelationship(customerId, request));
    }

    @GetMapping("/customers/{customerId}/360")
    public Customer360Response customer360(@PathVariable Long customerId) {
        return multiAssetService.customer360(customerId);
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<TransactionResponse> ingestTransaction(
            @Valid @RequestBody IngestTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(multiAssetService.ingestTransaction(request));
    }

    @GetMapping("/signals")
    public Page<RiskSignalResponse> listSignals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return multiAssetService.listSignals(page, size);
    }
}
