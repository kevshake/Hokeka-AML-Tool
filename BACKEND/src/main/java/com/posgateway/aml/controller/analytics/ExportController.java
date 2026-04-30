package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/exports")
public class ExportController {

    private final ComplianceCaseRepository caseRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final AuditLogRepository auditLogRepository;

    public ExportController(ComplianceCaseRepository caseRepository, SuspiciousActivityReportRepository sarRepository,
            AuditLogRepository auditLogRepository) {
        this.caseRepository = caseRepository;
        this.sarRepository = sarRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping(value = "/cases.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCases(@RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String merchantId) {
        List<ComplianceCase> cases = caseRepository.findAll();
        if (status != null && !status.isEmpty()) {
            try {
                CaseStatus cs = CaseStatus.valueOf(status);
                cases = cases.stream().filter(c -> cs.equals(c.getStatus())).toList();
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (priority != null && !priority.isEmpty()) {
            try {
                com.posgateway.aml.model.CasePriority cp = com.posgateway.aml.model.CasePriority.valueOf(priority);
                cases = cases.stream().filter(c -> cp.equals(c.getPriority())).toList();
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (merchantId != null && !merchantId.isEmpty()) {
            cases = cases.stream().filter(c -> merchantId.equals(c.getMerchantId())).toList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("caseReference,status,priority,merchantId,assignedTo,createdAt,updatedAt\n");
        for (ComplianceCase c : cases) {
            sb.append(safe(c.getCaseReference())).append(",")
                    .append(safe(c.getStatus() != null ? c.getStatus().name() : null)).append(",")
                    .append(safe(c.getPriority() != null ? c.getPriority().name() : null)).append(",")
                    .append(safe(c.getMerchantId())).append(",")
                    .append(safe(c.getAssignedTo() != null ? String.valueOf(c.getAssignedTo().getId()) : null))
                    .append(",")
                    .append(safe(c.getCreatedAt())).append(",")
                    .append(safe(c.getUpdatedAt())).append("\n");
        }
        return csvResponse("cases.csv", sb.toString());
    }

    @GetMapping(value = "/sars.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportSars(@RequestParam(required = false) String status) {
        List<SuspiciousActivityReport> sars = sarRepository.findAll();
        if (status != null && !status.isEmpty()) {
            try {
                SarStatus ss = SarStatus.valueOf(status);
                sars = sars.stream().filter(s -> ss.equals(s.getStatus())).toList();
            } catch (IllegalArgumentException ignored) {
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("sarReference,status,type,jurisdiction,filedAt,createdAt\n");
        for (SuspiciousActivityReport s : sars) {
            sb.append(safe(s.getSarReference())).append(",")
                    .append(safe(s.getStatus() != null ? s.getStatus().name() : null)).append(",")
                    .append(safe(s.getSarType() != null ? s.getSarType().name() : null)).append(",")
                    .append(safe(s.getJurisdiction())).append(",")
                    .append(safe(s.getFiledAt())).append(",")
                    .append(safe(s.getCreatedAt())).append("\n");
        }
        return csvResponse("sars.csv", sb.toString());
    }

    @GetMapping(value = "/audit.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportAudit(@RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDateTime from = start != null ? LocalDateTime.parse(start) : LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime to = end != null ? LocalDateTime.parse(end) : LocalDateTime.now();
        List<AuditLog> logs = auditLogRepository.findByTimestampBetween(from, to);
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp,user,action,entityType,entityId,success\n");
        for (AuditLog l : logs) {
            sb.append(safe(l.getTimestamp())).append(",")
                    .append(safe(l.getUsername())).append(",")
                    .append(safe(l.getActionType())).append(",")
                    .append(safe(l.getEntityType())).append(",")
                    .append(safe(l.getEntityId())).append(",")
                    .append(l.isSuccess()).append("\n");
        }
        return csvResponse("audit.csv", sb.toString());
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(bytes);
    }

    private String safe(Object o) {
        return o == null ? "" : o.toString().replace(",", " ");
    }
}
