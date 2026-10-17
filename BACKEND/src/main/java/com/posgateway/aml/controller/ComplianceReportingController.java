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
import org.springframework.security.access.prepost.PreAuthorize;

// @Slf4j removed
// @RequiredArgsConstructor removed
/**
 * SAR register. Tipping-off control: only compliance roles may read or change SARs — MLRO and
 * COMPLIANCE_OFFICER (PSP-scoped officers see their PSP's SARs only) plus platform administrators
 * (SUPER_ADMIN / ADMIN / PLATFORM_ADMIN via the role hierarchy). PSP operators (PSP_USER) and PSP
 * administrators are refused with 403.
 */
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','MLRO','COMPLIANCE_OFFICER')")
@RestController
@RequestMapping("/compliance/sar")
public class ComplianceReportingController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceReportingController.class);

    private final ComplianceReportingService reportingService;
    private final SuspiciousActivityReportRepository sarRepository;
    private final PspIsolationService pspIsolationService;
    private final com.posgateway.aml.mapper.SarMapper sarMapper;
    /** Live pull of the SAR's transactions when the SAR is viewed (R6). */
    private final com.posgateway.aml.service.edgedata.TransactionDetailsService transactionDetails;
    /** R9 filing from Edge pulls (the only path that marks a SAR FILED). */
    private final com.posgateway.aml.service.filing.RegulatoryFilingService filingService;
    /** R9 filed-document store access ("filed" status, stored XML of a filed SAR). */
    private final com.posgateway.aml.service.filing.FiledDocumentAccessService filedDocuments;
    /** R9 legal holds (released when a SAR draft is deleted). */
    private final com.posgateway.aml.service.legalhold.LegalHoldService legalHolds;

    public ComplianceReportingController(ComplianceReportingService reportingService,
            SuspiciousActivityReportRepository sarRepository,
            PspIsolationService pspIsolationService,
            com.posgateway.aml.mapper.SarMapper sarMapper,
            com.posgateway.aml.service.edgedata.TransactionDetailsService transactionDetails,
            com.posgateway.aml.service.filing.RegulatoryFilingService filingService,
            com.posgateway.aml.service.filing.FiledDocumentAccessService filedDocuments,
            com.posgateway.aml.service.legalhold.LegalHoldService legalHolds) {
        this.reportingService = reportingService;
        this.sarRepository = sarRepository;
        this.pspIsolationService = pspIsolationService;
        this.sarMapper = sarMapper;
        this.transactionDetails = transactionDetails;
        this.filingService = filingService;
        this.filedDocuments = filedDocuments;
        this.legalHolds = legalHolds;
    }

    /** Adds each filed SAR's filed-document metadata to a list of SAR responses (R9 "filed" status). */
    private <C extends java.util.Collection<SarResponse>> C withFiledDocuments(C responses) {
        java.util.List<Long> ids = responses.stream().map(SarResponse::getReportId)
                .filter(java.util.Objects::nonNull).toList();
        Map<Long, com.posgateway.aml.dto.compliance.FiledDocumentSummary> docs = filedDocuments.summariesOfSars(ids);
        responses.forEach(r -> r.setFiledDocument(docs.get(r.getReportId())));
        return responses;
    }

    /**
     * Get all SAR reports
     * GET /api/v1/compliance/sar
     */
    @GetMapping
    public ResponseEntity<?> getAllSars(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        log.info("Get SAR reports (status filter: {}, page: {}, size: {})", status, page, size);
        if (page != null || size != null) {
            int safePage = page != null ? page : 0;
            int safeSize = size != null ? size : 25;
            org.springframework.data.domain.Page<SarResponse> result = reportingService.getSarsPage(status, safePage,
                    safeSize);
            withFiledDocuments(result.getContent());
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(withFiledDocuments(reportingService.getAllSars(status)));
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

    /**
     * Files an APPROVED SAR through the R9 filing path (the same as {@code POST /api/v1/sar/{id}/file}):
     * the transactions are pulled from the Edge and the filed document stored before the SAR becomes
     * FILED. Optional body {@code {"regulatorReference": "..."}}.
     */
    @PostMapping("/{id}/file")
    public ResponseEntity<SarResponse> fileSar(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        log.info("File SAR request: {}", id);
        com.posgateway.aml.service.filing.RegulatoryFilingService.FilingResult result = filingService.fileSar(id,
                body == null ? null : body.get("regulatorReference"));
        SarResponse response = sarRepository.findById(id).map(sarMapper::toResponse).orElseGet(SarResponse::new);
        response.setFiledDocument(result.document());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(response);
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

    /**
     * SAR view with its transactions pulled live from the PSP's Edge (R6): the draft stores only ID
     * references, so {@code transactionDetails} is rendered per view and never persisted (plan
     * conflict 7); {@code transactionDetailsStatus} reports offline / partial / timeout / busy.
     * Platform staff need the PSP's support access and a {@code supportReason}.
     * GET /api/v1/compliance/sar/{id}/detail
     */
    @GetMapping("/{id}/detail")
    public ResponseEntity<SarResponse> getSarDetail(@PathVariable Long id,
            @RequestParam(required = false) Boolean transactionDetails,
            @RequestParam(required = false) String supportReason,
            @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader) {
        SuspiciousActivityReport sar = sarRepository.findById(id).orElse(null);
        if (sar == null || !(pspIsolationService.isPlatformAdministrator()
                || pspIsolationService.getCurrentUserPspId().equals(sar.getPspId()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        SarResponse response = sarMapper.toResponse(sar);
        // A filed SAR is shown from its stored filed document (R9): no Edge pull unless asked for.
        response.setFiledDocument(filedDocuments.summaryOf(
                com.posgateway.aml.entity.filing.RegulatoryFiledDocument.WorkflowType.SAR, id, sar.getPspId())
                .orElse(null));
        boolean pull = transactionDetails != null ? transactionDetails : response.getFiledDocument() == null;
        if (!pull) {
            response.setTransactionDetailsStatus(com.posgateway.aml.dto.edgedata.EdgePullStatus.notRequested());
        } else if (sar.getPspId() == null) {
            response.setTransactionDetailsStatus(com.posgateway.aml.dto.edgedata.EdgePullStatus.none());
        } else {
            com.posgateway.aml.service.edgedata.TransactionDetailsService.Details d = this.transactionDetails.forRecord(
                    sar.getPspId(), sar.getTransactionReferences(),
                    com.posgateway.aml.edge.tunnel.TunnelActor.Purpose.SAR_FILING,
                    com.posgateway.aml.service.edgedata.EdgeDataCallerResolver.WorkflowRefs.ofFiling(
                            sar.getSarReference() != null ? sar.getSarReference() : "SAR-" + sar.getId()),
                    supportReason != null && !supportReason.isBlank() ? supportReason : reasonHeader);
            response.setTransactionDetails(d.items());
            response.setTransactionDetailsStatus(d.status());
        }
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(response);
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<String> downloadSarXml(@PathVariable Long id,
            @RequestParam(required = false) String supportReason,
            @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader) {
        log.info("Download SAR XML: {}", id);
        // Tenant check before rendering: a PSP-scoped officer may only export their PSP's SAR.
        SuspiciousActivityReport sar = sarRepository.findById(id).orElse(null);
        if (sar == null || !(pspIsolationService.isPlatformAdministrator()
                || pspIsolationService.getCurrentUserPspId().equals(sar.getPspId()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        // A filed SAR exports its stored regulatory record exactly as filed (audited, no Edge pull).
        java.util.Optional<com.posgateway.aml.service.filing.FiledDocumentAccessService.Download> filed =
                filedDocuments.downloadFor(com.posgateway.aml.entity.filing.RegulatoryFiledDocument.WorkflowType.SAR,
                        id, supportReason != null && !supportReason.isBlank() ? supportReason : reasonHeader);
        if (filed.isPresent()) {
            return ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filed.get().fileName() + "\"")
                    .header("X-Content-SHA256", filed.get().sha256())
                    .contentType(MediaType.APPLICATION_XML)
                    .body(new String(filed.get().content(), java.nio.charset.StandardCharsets.UTF_8));
        }
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
        SuspiciousActivityReport sar = sarRepository.findById(id).orElse(null);
        if (sar == null) {
            return ResponseEntity.<Void>notFound().build();
        }
        if (!pspIsolationService.isPlatformAdministrator()
                && !pspIsolationService.getCurrentUserPspId().equals(sar.getPspId())) {
            return ResponseEntity.<Void>status(HttpStatus.FORBIDDEN).build();
        }
        // A filed SAR is part of the regulatory record (R9): it cannot be deleted.
        if (sar.getStatus() == SarStatus.FILED) {
            return ResponseEntity.<Void>status(HttpStatus.CONFLICT).build();
        }
        sarRepository.delete(sar);
        // The draft's legal holds were placed in error now that the SAR is gone.
        com.posgateway.aml.entity.User actor = pspIsolationService.getCurrentUser();
        legalHolds.releaseWorkflow(com.posgateway.aml.entity.filing.LegalHoldWorkflow.Type.SAR, id,
                com.posgateway.aml.entity.filing.LegalHold.ReleaseReason.PLACED_IN_ERROR,
                actor != null && actor.getId() != null ? "u-" + actor.getId() : null);
        return ResponseEntity.<Void>noContent().build();
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
