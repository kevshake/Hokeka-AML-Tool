package com.posgateway.aml.controller.export;

import com.posgateway.aml.dto.export.ConsoleListExportRequestDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.export.ConsoleListExportJob;
import com.posgateway.aml.service.export.ConsoleListExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * REST endpoints for Console list/record exports (CSV, XLSX, PDF) with async job support.
 */
@RestController
@RequestMapping("/console/exports")
@PreAuthorize("isAuthenticated()")
public class ConsoleListExportController {

    private final ConsoleListExportService exportService;

    public ConsoleListExportController(ConsoleListExportService exportService) {
        this.exportService = exportService;
    }

    /** Starts a synchronous stream or asynchronous job for a filtered Console list export. */
    @PostMapping
    public ResponseEntity<?> startExport(@AuthenticationPrincipal User user,
                                         @RequestBody ConsoleListExportRequestDto request,
                                         HttpServletRequest httpRequest) {
        return exportService.export(user, request, clientIp(httpRequest));
    }

    /** Polls async export job status until COMPLETED or FAILED. */
    @GetMapping("/jobs/{jobUuid}")
    public ResponseEntity<Map<String, Object>> jobStatus(@AuthenticationPrincipal User user,
                                                         @PathVariable String jobUuid) {
        ConsoleListExportJob job = exportService.jobStatus(jobUuid, user);
        return ResponseEntity.ok(Map.of(
                "jobUuid", job.getJobUuid(),
                "status", job.getStatus(),
                "rowCount", job.getRowCount() != null ? job.getRowCount() : 0,
                "fileName", job.getFileName() != null ? job.getFileName() : "",
                "errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : ""
        ));
    }

    /** Downloads a completed async export artifact. */
    @GetMapping("/jobs/{jobUuid}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal User user,
                                           @PathVariable String jobUuid) throws IOException {
        return exportService.downloadJob(jobUuid, user);
    }

    /** Extracts the client IP for audit logging, honoring reverse-proxy forwarded headers when present. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
