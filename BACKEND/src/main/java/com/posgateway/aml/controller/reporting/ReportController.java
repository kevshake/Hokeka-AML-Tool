package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.dto.reporting.*;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.reporting.ReportGenerationService;
import com.posgateway.aml.service.reporting.ReportHistoryService;
import com.posgateway.aml.service.reporting.ReportSchedulingService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Report Controller
 * REST endpoints for report generation, preview, history, scheduling, and chart data
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final ReportGenerationService reportGenerationService;
    private final ReportHistoryService reportHistoryService;
    private final ReportSchedulingService reportSchedulingService;
    private final PspIsolationService pspIsolationService;

    public ReportController(ReportGenerationService reportGenerationService,
                           ReportHistoryService reportHistoryService,
                           ReportSchedulingService reportSchedulingService,
                           PspIsolationService pspIsolationService) {
        this.reportGenerationService = reportGenerationService;
        this.reportHistoryService = reportHistoryService;
        this.reportSchedulingService = reportSchedulingService;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Preview report data
     * POST /api/reports/preview
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<ReportPreviewDTO> previewReport(@RequestBody ReportGenerateRequest request) {
        logger.info("Preview report request: {}", request.getReportType());
        
        Long pspId = pspIsolationService.sanitizePspId(request.getPspId());
        
        ReportPreviewDTO preview = reportGenerationService.previewReport(
            request.getReportType(), 
            request.getParameters(), 
            pspId
        );
        
        return ResponseEntity.ok(preview);
    }

    /**
     * Generate report
     * POST /api/reports/generate
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<ReportExecutionDTO> generateReport(@RequestBody ReportGenerateRequest request,
                                                               Authentication authentication) {
        logger.info("Generate report request: {}", request.getReportType());
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        Long pspId = pspIsolationService.sanitizePspId(request.getPspId());
        
        CompletableFuture<ReportExecutionDTO> future = reportGenerationService.generateReport(
            request.getReportType(),
            request.getParameters(),
            userId,
            pspId
        );
        
        // Return immediately with pending status
        ReportExecutionDTO pending = new ReportExecutionDTO();
        pending.setStatus(com.posgateway.aml.entity.reporting.ExecutionStatus.PENDING);
        pending.setTriggeredBy(userId);
        pending.setTriggeredByName(user.getFullName());
        
        return ResponseEntity.accepted().body(pending);
    }

    /**
     * Get report generation status
     * GET /api/reports/status/{executionId}
     */
    @GetMapping("/status/{executionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<ReportExecutionDTO> getExecutionStatus(@PathVariable String executionId) {
        logger.debug("Get execution status: {}", executionId);
        
        ReportExecutionDTO status = reportGenerationService.getReportExecutionStatus(executionId);
        return ResponseEntity.ok(status);
    }

    /**
     * Cancel report execution
     * POST /api/reports/cancel/{executionId}
     */
    @PostMapping("/cancel/{executionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<Map<String, Object>> cancelReport(@PathVariable String executionId) {
        logger.info("Cancel report request: {}", executionId);
        
        boolean cancelled = reportGenerationService.cancelReportExecution(executionId);
        
        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Report execution cancelled"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Unable to cancel report execution"
            ));
        }
    }

    /**
     * Get report history
     * GET /api/reports/history
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<Page<ReportExecutionDTO>> getReportHistory(
            @RequestParam(required = false) Long pspId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        
        logger.debug("Get report history for psp: {}, page: {}", pspId, page);
        
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        Page<ReportExecutionDTO> history = reportHistoryService.getReportHistory(
            effectivePspId, page, size, sortBy, sortDirection
        );
        
        return ResponseEntity.ok(history);
    }

    /**
     * Get report by ID
     * GET /api/reports/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<ReportExecutionDTO> getReportById(@PathVariable Long id) {
        logger.debug("Get report by ID: {}", id);
        
        ReportExecutionDTO report = reportHistoryService.getReportById(id);
        return ResponseEntity.ok(report);
    }

    /**
     * Delete report
     * DELETE /api/reports/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<Map<String, Object>> deleteReport(@PathVariable Long id) {
        logger.info("Delete report: {}", id);
        
        boolean deleted = reportHistoryService.deleteReport(id);
        
        return ResponseEntity.ok(Map.of(
            "success", deleted,
            "message", deleted ? "Report deleted successfully" : "Failed to delete report"
        ));
    }

    /**
     * Download report file
     * GET /api/reports/download/{id}
     */
    @GetMapping("/download/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long id,
                                                      @RequestParam(required = false) String format) {
        logger.info("Download report: {}, format: {}", id, format);
        
        return reportHistoryService.downloadReport(id, format);
    }

    /**
     * Schedule recurring report
     * POST /api/reports/schedule
     */
    @PostMapping("/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<ReportScheduleDTO> scheduleReport(@RequestBody ReportScheduleRequest request,
                                                              Authentication authentication) {
        logger.info("Schedule report request: {}", request.getReportId());
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        Long pspId = pspIsolationService.sanitizePspId(request.getPspId());
        
        ReportScheduleDTO schedule = reportSchedulingService.scheduleReport(
            request.getReportId(),
            request,
            request.getDefaultParameters(),
            userId,
            pspId
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(schedule);
    }

    /**
     * Update schedule
     * PUT /api/reports/schedule/{scheduleId}
     */
    @PutMapping("/schedule/{scheduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<ReportScheduleDTO> updateSchedule(@PathVariable Long scheduleId,
                                                              @RequestBody ReportScheduleRequest request) {
        logger.info("Update schedule: {}", scheduleId);
        
        ReportScheduleDTO schedule = reportSchedulingService.updateSchedule(scheduleId, request);
        return ResponseEntity.ok(schedule);
    }

    /**
     * Get scheduled reports
     * GET /api/reports/schedule
     */
    @GetMapping("/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<Page<ReportScheduleDTO>> getScheduledReports(
            @RequestParam(required = false) Long pspId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        
        logger.debug("Get scheduled reports for psp: {}", pspId);
        
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        Page<ReportScheduleDTO> schedules = reportSchedulingService.getScheduledReports(
            effectivePspId, page, size, sortBy, sortDirection
        );
        
        return ResponseEntity.ok(schedules);
    }

    /**
     * Get schedule by ID
     * GET /api/reports/schedule/{scheduleId}
     */
    @GetMapping("/schedule/{scheduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<ReportScheduleDTO> getScheduleById(@PathVariable Long scheduleId) {
        logger.debug("Get schedule by ID: {}", scheduleId);
        
        ReportScheduleDTO schedule = reportSchedulingService.getScheduleById(scheduleId);
        return ResponseEntity.ok(schedule);
    }

    /**
     * Unschedule (deactivate) a report
     * DELETE /api/reports/schedule/{scheduleId}
     */
    @DeleteMapping("/schedule/{scheduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<Map<String, Object>> unscheduleReport(@PathVariable Long scheduleId) {
        logger.info("Unschedule report: {}", scheduleId);
        
        boolean unscheduled = reportSchedulingService.unscheduleReport(scheduleId);
        
        return ResponseEntity.ok(Map.of(
            "success", unscheduled,
            "message", unscheduled ? "Schedule deactivated successfully" : "Failed to deactivate schedule"
        ));
    }

    /**
     * Activate a schedule
     * POST /api/reports/schedule/{scheduleId}/activate
     */
    @PostMapping("/schedule/{scheduleId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<ReportScheduleDTO> activateSchedule(@PathVariable Long scheduleId) {
        logger.info("Activate schedule: {}", scheduleId);
        
        ReportScheduleDTO schedule = reportSchedulingService.activateSchedule(scheduleId);
        return ResponseEntity.ok(schedule);
    }

    /**
     * Get chart data
     * POST /api/reports/chart
     */
    @PostMapping("/chart")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<ChartDataDTO> getChartData(@RequestBody ChartDataDTO request) {
        logger.info("Get chart data for report: {}", request.getReportType());
        
        // This would typically query the report data and format it for charts
        // For now, returning the request as a placeholder
        return ResponseEntity.ok(request);
    }

    /**
     * Get report execution statistics
     * GET /api/reports/statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<ReportHistoryService.ExecutionStatistics> getExecutionStatistics(
            @RequestParam(required = false) Long pspId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        logger.debug("Get execution statistics for psp: {}", pspId);
        
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        java.time.LocalDateTime fromDate = from != null ? 
            java.time.LocalDateTime.parse(from) : java.time.LocalDateTime.now().minusMonths(1);
        java.time.LocalDateTime toDate = to != null ? 
            java.time.LocalDateTime.parse(to) : java.time.LocalDateTime.now();
        
        ReportHistoryService.ExecutionStatistics stats = reportHistoryService.getExecutionStatistics(
            effectivePspId, fromDate, toDate
        );
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Retry failed report
     * POST /api/reports/{id}/retry
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<ReportExecutionDTO> retryReport(@PathVariable Long id) {
        logger.info("Retry report: {}", id);
        
        ReportExecutionDTO result = reportHistoryService.retryReport(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Get recent reports
     * GET /api/reports/recent
     */
    @GetMapping("/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<List<ReportExecutionDTO>> getRecentReports(Authentication authentication,
                                                                          @RequestParam(defaultValue = "10") int limit) {
        User user = (User) authentication.getPrincipal();
        logger.debug("Get recent reports for user: {}", user.getId());
        
        List<ReportExecutionDTO> recent = reportHistoryService.getRecentReports(user.getId(), limit);
        return ResponseEntity.ok(recent);
    }
}
