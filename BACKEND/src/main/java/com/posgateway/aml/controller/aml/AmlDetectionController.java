package com.posgateway.aml.controller.aml;

import com.posgateway.aml.service.aml.AmlScenarioDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AML Detection Controller
 * Provides endpoints for AML scenario detection
 */
@RestController
@RequestMapping("/aml/detection")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'ANALYST', 'INVESTIGATOR')")
public class AmlDetectionController {

    private final AmlScenarioDetectionService amlDetectionService;

    @Autowired
    public AmlDetectionController(AmlScenarioDetectionService amlDetectionService) {
        this.amlDetectionService = amlDetectionService;
    }

    @GetMapping("/structuring/{merchantId}")
    public ResponseEntity<List<AmlScenarioDetectionService.StructuringDetection>> detectStructuring(
            @PathVariable String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(1);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(amlDetectionService.detectStructuring(merchantId, startDate, endDate));
    }

    @GetMapping("/rapid-movement/{merchantId}")
    public ResponseEntity<List<AmlScenarioDetectionService.RapidMovementDetection>> detectRapidMovement(
            @PathVariable String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusDays(7);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(amlDetectionService.detectRapidMovement(merchantId, startDate, endDate));
    }

    @GetMapping("/round-dollar/{merchantId}")
    public ResponseEntity<List<AmlScenarioDetectionService.RoundDollarDetection>> detectRoundDollar(
            @PathVariable String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(1);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(amlDetectionService.detectRoundDollar(merchantId, startDate, endDate));
    }

    @GetMapping("/funnel-accounts/{merchantId}")
    public ResponseEntity<List<AmlScenarioDetectionService.FunnelAccountDetection>> detectFunnelAccounts(
            @PathVariable String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(1);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(amlDetectionService.detectFunnelAccounts(merchantId, startDate, endDate));
    }

    @GetMapping("/trade-based-ml/{merchantId}")
    public ResponseEntity<List<AmlScenarioDetectionService.TradeBasedMlDetection>> detectTradeBasedMl(
            @PathVariable String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(1);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(amlDetectionService.detectTradeBasedMl(merchantId, startDate, endDate));
    }
}

