package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Batch sanctions screening endpoint.
 *
 * <p>Screens a list of merchants through the platform's own independent sanctions
 * engine ({@code aml-microservice}). There is no external KYC vendor: results carry
 * the real engine status, including {@code UNAVAILABLE} when the engine is down, so a
 * caller can never mistake an outage for a clean screen.
 */
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/screening")
public class BatchScreeningController {

    private final AerospikeSanctionsScreeningService screeningEngine;

    public BatchScreeningController(AerospikeSanctionsScreeningService screeningEngine) {
        this.screeningEngine = screeningEngine;
    }

    @PostMapping("/batch")
    public ResponseEntity<List<ScreeningResult>> screenBatch(@RequestBody List<Merchant> merchants) {
        List<ScreeningResult> results = merchants.stream()
                .map(m -> screeningEngine.screenMerchant(m.getLegalName(), m.getTradingName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }
}
