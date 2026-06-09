package com.posgateway.aml.controller.chargeback;

import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated read API for chargeback / Verifi RDR disputes ingested via webhook.
 */
@RestController
@RequestMapping("/chargeback/disputes")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_USER')")
public class ChargebackDisputeController {

    private final ChargebackDisputeRepository disputeRepository;
    private final PspIsolationService pspIsolationService;

    public ChargebackDisputeController(ChargebackDisputeRepository disputeRepository,
                                       PspIsolationService pspIsolationService) {
        this.disputeRepository = disputeRepository;
        this.pspIsolationService = pspIsolationService;
    }

    @GetMapping
    public ResponseEntity<List<ChargebackDispute>> listDisputes(
            @RequestParam(required = false) Long merchantId) {

        if (merchantId != null) {
            return ResponseEntity.ok(disputeRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId));
        }

        if (!pspIsolationService.isPlatformAdministrator()) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (pspId != null) {
                return ResponseEntity.ok(disputeRepository.findByPspIdOrderByCreatedAtDesc(pspId));
            }
        }

        return ResponseEntity.ok(disputeRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChargebackDispute> getDispute(@PathVariable Long id) {
        return disputeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
