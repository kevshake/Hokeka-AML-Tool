package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.entity.reporting.ReportResult;
import com.posgateway.aml.repository.reporting.ReportResultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports/results")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
public class ReportResultController {

    private final ReportResultRepository resultRepository;
    private final com.posgateway.aml.repository.reporting.ReportExecutionRepository executionRepository;
    private final com.posgateway.aml.service.security.PspIsolationService pspIsolationService;

    public ReportResultController(ReportResultRepository resultRepository,
            com.posgateway.aml.repository.reporting.ReportExecutionRepository executionRepository,
            com.posgateway.aml.service.security.PspIsolationService pspIsolationService) {
        this.resultRepository = resultRepository;
        this.executionRepository = executionRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /** Tenant isolation: resolve the execution's PSP and validate the caller may read it. */
    private void guardExecution(Long executionId) {
        com.posgateway.aml.entity.reporting.ReportExecution exec = executionRepository.findById(executionId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Report execution not found"));
        pspIsolationService.validatePspAccess(exec.getPspId());
    }

    @GetMapping("/execution/{executionId}")
    public List<ReportResult> listByExecution(@PathVariable Long executionId) {
        guardExecution(executionId);
        return resultRepository.findByExecutionIdOrderByRowNumberAsc(executionId);
    }

    @GetMapping("/execution/{executionId}/page")
    public Page<ReportResult> pageByExecution(@PathVariable Long executionId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "100") int size) {
        guardExecution(executionId);
        return resultRepository.findByExecutionId(executionId, PageRequest.of(page, size));
    }

    /**
     * Report result rows feed regulatory filing generation (e.g. FRC goAML XML) and are
     * normally written by the report-generation pipeline, not by clients. Restrict manual
     * writes to platform admins so a PSP user cannot forge/overwrite rows in any execution.
     */
    @PostMapping("/execution/{executionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ReportResult> saveRow(@PathVariable Long executionId,
                                                @RequestBody Map<String, Object> body) {
        Object rowNumberRaw = body == null ? null : body.get("rowNumber");
        if (rowNumberRaw == null) {
            return ResponseEntity.badRequest().build();
        }
        ReportResult row = new ReportResult();
        row.setExecutionId(executionId);
        try {
            row.setRowNumber(Integer.valueOf(rowNumberRaw.toString()));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = body.get("data") instanceof Map ? (Map<String, Object>) body.get("data") : null;
        row.setData(data);
        return ResponseEntity.ok(resultRepository.save(row));
    }
}
