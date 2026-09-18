package com.posgateway.aml.controller.crypto;

import com.posgateway.aml.dto.crypto.VirtualAssetDtos.*;
import com.posgateway.aml.entity.crypto.WalletScreeningTrigger;
import com.posgateway.aml.service.crypto.VirtualAssetComplianceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/virtual-assets")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_USER')")
public class VirtualAssetComplianceController {
    private final VirtualAssetComplianceService service;
    public VirtualAssetComplianceController(VirtualAssetComplianceService service) { this.service = service; }

    @GetMapping("/vasps") public Page<VaspResponse> vasps(@RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.listVasps(search, page, size);
    }
    @PostMapping("/vasps") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public ResponseEntity<VaspResponse> createVasp(@Valid @RequestBody SaveVaspRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveVasp(null, request));
    }
    @PutMapping("/vasps/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public VaspResponse updateVasp(@PathVariable Long id, @Valid @RequestBody SaveVaspRequest request) { return service.saveVasp(id, request); }
    @PostMapping("/vasps/{id}/screen") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN')")
    public VaspResponse screenVasp(@PathVariable Long id) { return service.screenVasp(id); }
    @GetMapping("/vasp-screenings") public Page<VaspScreeningResponse> vaspScreenings(
            @RequestParam(required = false) Long vaspId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return service.vaspScreeningHistory(vaspId, page, size); }

    @GetMapping("/wallets") public Page<WalletProfileResponse> wallets(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return service.listWallets(page, size); }
    @PostMapping("/wallets") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<WalletProfileResponse> registerWallet(@Valid @RequestBody RegisterWalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerWallet(request));
    }
    @GetMapping("/transactions") public Page<CryptoTransactionSummary> transactions(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return service.listCryptoTransactions(page, size);
    }
    @PostMapping("/wallets/{id}/screen") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN')")
    public WalletScreeningResponse screen(@PathVariable Long id,
            @RequestParam(defaultValue = "MANUAL") WalletScreeningTrigger trigger,
            @RequestParam(required = false) Long transactionId) { return service.screenWallet(id, trigger, transactionId); }
    @GetMapping("/wallet-screenings") public Page<WalletScreeningResponse> screenings(
            @RequestParam(required = false) Long walletId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return service.screeningHistory(walletId, page, size); }

    @GetMapping("/travel-rule/policies") public List<TravelRulePolicyResponse> policies() { return service.listPolicies(); }
    @PostMapping("/travel-rule/policies") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public ResponseEntity<TravelRulePolicyResponse> createPolicy(@Valid @RequestBody SaveTravelRulePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.savePolicy(null, request));
    }
    @PutMapping("/travel-rule/policies/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public TravelRulePolicyResponse updatePolicy(@PathVariable Long id, @Valid @RequestBody SaveTravelRulePolicyRequest request) { return service.savePolicy(id, request); }
    @GetMapping("/travel-rule/transfers") public Page<TravelRuleTransferResponse> transfers(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) { return service.listTransfers(page, size); }
    @PostMapping("/travel-rule/transfers") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<TravelRuleTransferResponse> prepare(@Valid @RequestBody PrepareTravelRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.prepareTransfer(request));
    }
    @PutMapping("/travel-rule/transfers/{id}/verify") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','PSP_ADMIN')")
    public TravelRuleTransferResponse verify(@PathVariable Long id, @Valid @RequestBody VerifyIdentityRequest request) { return service.verifyIdentity(id, request); }
    @PostMapping("/travel-rule/transfers/{id}/transmit") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public TravelRuleTransferResponse transmit(@PathVariable Long id) { return service.transmit(id); }

    @GetMapping("/regulator-grants") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public List<RegulatorGrantResponse> grants() { return service.listGrants(); }
    @PostMapping("/regulator-grants") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public ResponseEntity<RegulatorGrantResponse> createGrant(@Valid @RequestBody CreateRegulatorGrantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGrant(request));
    }
    @DeleteMapping("/regulator-grants/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public ResponseEntity<Void> revoke(@PathVariable Long id) { service.revokeGrant(id); return ResponseEntity.noContent().build(); }
}
