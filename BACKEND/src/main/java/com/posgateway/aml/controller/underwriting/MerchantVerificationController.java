package com.posgateway.aml.controller.underwriting;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.underwriting.MerchantVerificationSignal;
import com.posgateway.aml.model.underwriting.UnderwritingOutcome;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.underwriting.MerchantVerificationSignalRepository;
import com.posgateway.aml.service.underwriting.MerchantVerificationOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Merchant underwriting verification API.
 *
 * <p>{@code POST /underwriting/merchants/{id}/verify} runs the full verification
 * orchestrator (internal checks + fail-closed external adapters), persists the evidence,
 * and returns the explainable decision. {@code GET .../signals} reads the append-only
 * signal history so an investigator can see exactly what fired.
 */
@RestController
@RequestMapping("/underwriting/merchants")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','INVESTIGATOR')")
public class MerchantVerificationController {

    private final MerchantVerificationOrchestrator orchestrator;
    private final MerchantRepository merchantRepository;
    private final MerchantVerificationSignalRepository signalRepository;

    public MerchantVerificationController(MerchantVerificationOrchestrator orchestrator,
                                          MerchantRepository merchantRepository,
                                          MerchantVerificationSignalRepository signalRepository) {
        this.orchestrator = orchestrator;
        this.merchantRepository = merchantRepository;
        this.signalRepository = signalRepository;
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<UnderwritingOutcome> verify(@PathVariable Long id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        return ResponseEntity.ok(orchestrator.verify(merchant));
    }

    @GetMapping("/{id}/signals")
    public ResponseEntity<List<MerchantVerificationSignal>> signals(@PathVariable Long id) {
        return ResponseEntity.ok(signalRepository.findByMerchantIdOrderByObservedAtDesc(id));
    }
}
