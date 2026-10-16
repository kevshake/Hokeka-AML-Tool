package com.posgateway.aml.controller.chargeback;

import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated read API for chargeback / Verifi RDR disputes ingested via webhook.
 */
@RestController
@RequestMapping("/chargeback/disputes")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'ANALYST', 'PSP_ADMIN', 'PSP_USER')")
public class ChargebackDisputeController {

    private final ChargebackDisputeRepository disputeRepository;
    private final PspIsolationService pspIsolationService;

    public ChargebackDisputeController(ChargebackDisputeRepository disputeRepository,
                                       PspIsolationService pspIsolationService) {
        this.disputeRepository = disputeRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /** Lists disputes with server-side pagination and optional merchant filter. */
    @GetMapping
    public ResponseEntity<?> listDisputes(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (merchantId != null) {
            Page<ChargebackDispute> slice = disputeRepository.findByMerchantId(merchantId, pageable);
            return ResponseEntity.ok(slice);
        }

        if (!pspIsolationService.isPlatformAdministrator()) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (pspId != null) {
                return ResponseEntity.ok(disputeRepository.findByPspId(pspId, pageable));
            }
        }

        return ResponseEntity.ok(disputeRepository.findAll(pageable));
    }

    /** Returns one dispute row after PSP access checks. */
    @GetMapping("/{id}")
    public ResponseEntity<ChargebackDispute> getDispute(@PathVariable Long id) {
        return disputeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
