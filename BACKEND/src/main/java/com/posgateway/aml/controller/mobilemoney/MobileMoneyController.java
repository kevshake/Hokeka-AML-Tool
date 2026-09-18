package com.posgateway.aml.controller.mobilemoney;

import com.posgateway.aml.dto.mobilemoney.MobileMoneyDtos.*;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyEntityType;
import com.posgateway.aml.service.mobilemoney.MobileMoneyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mobile-money")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_USER')")
public class MobileMoneyController {
    private final MobileMoneyService mobileMoneyService;

    public MobileMoneyController(MobileMoneyService mobileMoneyService) {
        this.mobileMoneyService = mobileMoneyService;
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<MobileMoneyAssessmentResponse> ingest(
            @Valid @RequestBody IngestMobileMoneyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mobileMoneyService.ingest(request));
    }

    @GetMapping("/transactions")
    public Page<MobileMoneyContextResponse> listTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return mobileMoneyService.listContexts(page, size);
    }

    @GetMapping("/risk-profiles")
    public Page<MobileMoneyRiskProfileResponse> listRiskProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return mobileMoneyService.listRiskProfiles(page, size);
    }

    @GetMapping("/network")
    public MobileMoneyNetworkResponse network(
            @RequestParam MobileMoneyEntityType entityType,
            @RequestParam String entityReference) {
        return mobileMoneyService.network(entityType, entityReference);
    }
}
