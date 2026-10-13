package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.dto.export.ConsoleListExportRequestDto;
import com.posgateway.aml.dto.export.ConsoleListViewType;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.export.ConsoleListExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Legacy CSV export paths retained for backward compatibility; all routes delegate to the paged Console exporter.
 */
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/exports")
public class ExportController {

    private final ConsoleListExportService consoleListExportService;

    public ExportController(ConsoleListExportService consoleListExportService) {
        this.consoleListExportService = consoleListExportService;
    }

    /** Streams cases CSV via the Console list exporter using optional legacy query filters. */
    @GetMapping(value = "/cases.csv", produces = "text/csv")
    public ResponseEntity<?> exportCases(@AuthenticationPrincipal User user,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String priority,
                                         @RequestParam(required = false) String merchantId,
                                         HttpServletRequest request) {
        Map<String, String> filters = new HashMap<>();
        if (status != null) {
            filters.put("status", status);
        }
        if (priority != null) {
            filters.put("priority", priority);
        }
        if (merchantId != null) {
            filters.put("merchantId", merchantId);
        }
        return delegate(user, ConsoleListViewType.CASES, filters, request);
    }

    /** Streams SAR CSV via the Console list exporter using optional status filter. */
    @GetMapping(value = "/sars.csv", produces = "text/csv")
    public ResponseEntity<?> exportSars(@AuthenticationPrincipal User user,
                                        @RequestParam(required = false) String status,
                                        HttpServletRequest request) {
        Map<String, String> filters = new HashMap<>();
        if (status != null) {
            filters.put("status", status);
        }
        return delegate(user, ConsoleListViewType.SARS, filters, request);
    }

    /** Streams audit CSV via the Console list exporter using optional date window filters. */
    @GetMapping(value = "/audit.csv", produces = "text/csv")
    public ResponseEntity<?> exportAudit(@AuthenticationPrincipal User user,
                                         @RequestParam(required = false) String start,
                                         @RequestParam(required = false) String end,
                                         HttpServletRequest request) {
        Map<String, String> filters = new HashMap<>();
        if (start != null) {
            filters.put("start", start);
        }
        if (end != null) {
            filters.put("end", end);
        }
        return delegate(user, ConsoleListViewType.AUDIT_LOGS, filters, request);
    }

    /** Delegates a legacy CSV path to the unified Console export orchestrator. */
    private ResponseEntity<?> delegate(User user,
                                       ConsoleListViewType viewType,
                                       Map<String, String> filters,
                                       HttpServletRequest request) {
        ConsoleListExportRequestDto dto = new ConsoleListExportRequestDto(viewType, "CSV", filters, false, null, null);
        return consoleListExportService.export(user, dto, clientIp(request));
    }

    /** Resolves client IP for export audit rows, honoring reverse-proxy headers when present. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
