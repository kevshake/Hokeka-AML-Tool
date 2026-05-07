package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.dto.compliance.CbkReportPage;
import com.posgateway.aml.dto.compliance.CbkSubmissionDto;
import com.posgateway.aml.dto.compliance.CbkSubmitRequest;
import com.posgateway.aml.dto.compliance.CbkSubmitResponse;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.cbk.CbkEndpointType;
import com.posgateway.aml.service.cbk.CbkSubmissionOrchestrator;
import com.posgateway.aml.service.cbk.CbkSubmissionResult;
import com.posgateway.aml.service.compliance.CbkReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CBK (Central Bank of Kenya) regulatory reporting API.
 *
 * <p>Backs the {@code CbkSubmissionPanel} on the Reports Center page. The
 * application context-path is {@code /api/v1}, so the public URLs are
 * {@code GET /api/v1/compliance/cbk/reports} and
 * {@code POST /api/v1/compliance/cbk/reports/submit}.
 *
 * <p>Tenant isolation: every request is scoped to the authenticated user's PSP.
 * Users without a PSP context (e.g. SUPER_ADMIN with no PSP attached) get an
 * empty list and a 400 on submit.
 */
@RestController
@RequestMapping("/compliance/cbk")
@PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
public class CbkReportController {

    private static final Logger log = LoggerFactory.getLogger(CbkReportController.class);

    private final CbkReportService cbkReportService;
    private final CbkSubmissionOrchestrator cbkSubmissionOrchestrator;

    public CbkReportController(CbkReportService cbkReportService,
                                CbkSubmissionOrchestrator cbkSubmissionOrchestrator) {
        this.cbkReportService = cbkReportService;
        this.cbkSubmissionOrchestrator = cbkSubmissionOrchestrator;
    }

    @GetMapping("/reports")
    public ResponseEntity<CbkReportPage> listReports(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        Long pspId = (user.getPsp() != null) ? user.getPsp().getPspId() : null;
        if (pspId == null) {
            log.debug("CBK list: user {} has no PSP context, returning empty page", user.getUsername());
            return ResponseEntity.ok(CbkReportPage.of(List.of()));
        }

        List<CbkSubmissionDto> rows = cbkReportService.listReports(pspId, period, from, to);
        return ResponseEntity.ok(CbkReportPage.of(rows));
    }

    @PostMapping("/reports/submit")
    public ResponseEntity<CbkSubmitResponse> submit(@RequestBody CbkSubmitRequest req) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        Long pspId = (user.getPsp() != null) ? user.getPsp().getPspId() : null;
        if (pspId == null) {
            return ResponseEntity.badRequest().build();
        }

        CbkSubmitResponse resp = cbkReportService.submitReport(pspId, user.getId(), req);
        log.info("CBK submission accepted by user {} for PSP {}: ref={}",
                user.getUsername(), pspId, resp.referenceNumber());
        return ResponseEntity.ok(resp);
    }

    /**
     * Manual retry / ad-hoc trigger for a single CBK GDI endpoint.
     *
     * <p>Allows admins and compliance officers to re-fire a specific endpoint for a
     * given PSP without waiting for the next scheduled run. Useful for recovering
     * from transient network failures or correcting data after an ad-hoc fix.
     *
     * <p>URL: {@code POST /api/v1/compliance/cbk/submissions/{endpointType}/run}
     *
     * @param endpointType path-variable matching a {@link CbkEndpointType} constant
     * @param body         JSON body containing {@code "pspId": <long>}
     * @return the {@link CbkSubmissionResult} for the triggered submission attempt
     */
    @PostMapping("/submissions/{endpointType}/run")
    @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN')")
    public ResponseEntity<CbkSubmissionResult> runSingleEndpoint(
            @PathVariable CbkEndpointType endpointType,
            @RequestBody Map<String, Long> body) {

        Long pspId = body.get("pspId");
        if (pspId == null) {
            log.warn("CBK manual run: missing pspId in request body for endpoint {}", endpointType);
            return ResponseEntity.badRequest().build();
        }

        log.info("CBK manual run triggered: endpoint={} pspId={}", endpointType, pspId);
        CbkSubmissionResult result = cbkSubmissionOrchestrator.runSingleEndpoint(pspId, endpointType);
        log.info("CBK manual run completed: endpoint={} pspId={} outcome={}",
                endpointType, pspId, result.getOutcome());

        return ResponseEntity.ok(result);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return (User) auth.getPrincipal();
        }
        return null;
    }
}
