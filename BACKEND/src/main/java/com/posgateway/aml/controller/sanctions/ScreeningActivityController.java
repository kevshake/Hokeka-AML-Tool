package com.posgateway.aml.controller.sanctions;

import com.posgateway.aml.entity.sanctions.ScreeningActivity;
import com.posgateway.aml.repository.sanctions.ScreeningActivityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regulator-facing screening activity API (persisted transaction-time and batch runs).
 */
@RestController
@RequestMapping("/screening/activity")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','SCREENING_ANALYST','INVESTIGATOR','ANALYST')")
public class ScreeningActivityController {

    private final ScreeningActivityRepository screeningActivityRepository;

    public ScreeningActivityController(ScreeningActivityRepository screeningActivityRepository) {
        this.screeningActivityRepository = screeningActivityRepository;
    }

    /** Lists screening runs filtered by transaction and/or merchant id. */
    @GetMapping
    public ResponseEntity<Page<ScreeningActivity>> listActivity(
            @RequestParam(required = false) Long txnId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (txnId != null) {
            return ResponseEntity.ok(screeningActivityRepository.findByTxnIdOrderByCreatedAtDesc(txnId, pageable));
        }
        if (merchantId != null) {
            return ResponseEntity.ok(screeningActivityRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable));
        }
        return ResponseEntity.badRequest().build();
    }
}
