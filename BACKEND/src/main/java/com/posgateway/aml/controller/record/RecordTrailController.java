package com.posgateway.aml.controller.record;

import com.posgateway.aml.dto.record.RecordTrailDtos.RecordDetail;
import com.posgateway.aml.service.record.RecordTrailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/records")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','MLRO','INVESTIGATOR','ANALYST','SCREENING_ANALYST','PSP_ADMIN','PSP_ANALYST','PSP_USER','AUDITOR','VIEWER')")
public class RecordTrailController {
    private final RecordTrailService recordTrailService;
    public RecordTrailController(RecordTrailService recordTrailService) { this.recordTrailService = recordTrailService; }

    @GetMapping("/{recordType}/{recordId}")
    public ResponseEntity<RecordDetail> detail(@PathVariable String recordType, @PathVariable String recordId) {
        return ResponseEntity.ok(recordTrailService.getDetail(recordType, recordId));
    }
}
