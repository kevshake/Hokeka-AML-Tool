package com.posgateway.aml.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.posgateway.aml.dto.compliance.CreateSarRequest;
import com.posgateway.aml.dto.compliance.SarResponse;
import com.posgateway.aml.dto.compliance.UpdateSarRequest;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.compliance.ComplianceReportingService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// @Slf4j removed
// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/compliance/sar")
public class ComplianceReportingController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceReportingController.class);

    private final ComplianceReportingService reportingService;
    private final SuspiciousActivityReportRepository sarRepository;
    private final PspIsolationService pspIsolationService;

    public ComplianceReportingController(ComplianceReportingService reportingService,
            SuspiciousActivityReportRepository sarRepository,
            PspIsolationService pspIsolationService) {
        this.reportingService = reportingService;
        this.sarRepository = sarRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Get all SAR reports
     * GET /api/v1/compliance/sar
     */
    @GetMapping
    public ResponseEntity<List<SarResponse>> getAllSars(
            @RequestParam(required = false) String status) {
        log.info("Get all SAR reports (status filter: {})", status);
        return ResponseEntity.ok(reportingService.getAllSars(status));
    }

    @PostMapping
    public ResponseEntity<SarResponse> createSar(@RequestBody CreateSarRequest request) {
        log.info("Create SAR request for merchant: {}", request.getMerchantId());
        return ResponseEntity.ok(reportingService.createSar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SarResponse> updateSar(@PathVariable Long id, @RequestBody UpdateSarRequest request) {
        log.info("Update SAR request: {}", id);
        return ResponseEntity.ok(reportingService.updateSar(id, request));
    }

    @PostMapping("/{id}/file")
    public ResponseEntity<SarResponse> fileSar(@PathVariable Long id) {
        log.info("File SAR request: {}", id);
        return ResponseEntity.ok(reportingService.fileSar(id));
    }

    /**
     * Get SAR by ID
     * GET /api/v1/compliance/sar/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<SuspiciousActivityReport> getSarById(@PathVariable Long id) {
        log.info("Get SAR by ID: {}", id);
        return sarRepository.findById(id)
                .filter(sar -> pspIsolationService.isPlatformAdministrator()
                        || pspIsolationService.getCurrentUserPspId().equals(sar.getPspId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<String> downloadSarXml(@PathVariable Long id) {
        log.info("Download SAR XML: {}", id);
        String xml = reportingService.generateFincenXml(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sar_" + id + ".xml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    /**
     * Delete SAR
     * DELETE /api/v1/compliance/sar/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSar(@PathVariable Long id) {
        log.info("Delete SAR: {}", id);
        return sarRepository.findById(id)
                .map(sar -> {
                    if (!pspIsolationService.isPlatformAdministrator()
                            && !pspIsolationService.getCurrentUserPspId().equals(sar.getPspId())) {
                        return ResponseEntity.<Void>status(HttpStatus.FORBIDDEN).build();
                    }
                    sarRepository.delete(sar);
                    return ResponseEntity.<Void>noContent().<Void>build();
                })
                .orElse(ResponseEntity.<Void>notFound().build());
    }

    /**
     * Get count of SAR reports that are not yet exported (not FILED)
     * GET /api/v1/compliance/sar/count/not-exported
     */
    @GetMapping("/count/not-exported")
    public ResponseEntity<Map<String, Long>> getNotExportedSarCount() {
        long totalCount;
        long filedCount;
        if (pspIsolationService.isPlatformAdministrator()) {
            totalCount = sarRepository.count();
            filedCount = sarRepository.countByStatus(SarStatus.FILED);
        } else {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            totalCount = sarRepository.countByPspId(pspId);
            filedCount = sarRepository.countByPspIdAndStatus(pspId, SarStatus.FILED);
        }
        Map<String, Long> response = new HashMap<>();
        response.put("count", totalCount - filedCount);
        return ResponseEntity.ok(response);
    }
}
