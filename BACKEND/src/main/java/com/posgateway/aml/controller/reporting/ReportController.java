package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.dto.reporting.*;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.entity.reporting.ReportExecution;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.service.reporting.ReportExportService;
import com.posgateway.aml.service.reporting.ReportGenerationService;
import com.posgateway.aml.service.reporting.ReportHistoryService;
import com.posgateway.aml.service.reporting.ReportSchedulingService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Report Controller
 * REST endpoints for report generation, preview, history, scheduling, and chart data
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final ReportGenerationService reportGenerationService;
    private final ReportHistoryService reportHistoryService;
    private final ReportSchedulingService reportSchedulingService;
    private final PspIsolationService pspIsolationService;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final ReportExportService reportExportService;
    private final ReportExecutionRepository reportExecutionRepository;

    public ReportController(ReportGenerationService reportGenerationService,
                           ReportHistoryService reportHistoryService,
                           ReportSchedulingService reportSchedulingService,
                           PspIsolationService pspIsolationService,
                           TransactionRepository transactionRepository,
                           AlertRepository alertRepository,
                           ReportExportService reportExportService,
                           ReportExecutionRepository reportExecutionRepository) {
        this.reportGenerationService = reportGenerationService;
        this.reportHistoryService = reportHistoryService;
        this.reportSchedulingService = reportSchedulingService;
        this.pspIsolationService = pspIsolationService;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.reportExportService = reportExportService;
        this.reportExecutionRepository = reportExecutionRepository;
    }

    /**
     * Preview report data
     * POST /api/reports/preview
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
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
        pending.setStatus(ExecutionStatus.PENDING);
        pending.setTriggeredBy(userId);
        pending.setTriggeredByName(user.getFullName());
        
        return ResponseEntity.accepted().body(pending);
    }

    /**
     * Get report generation status
     * GET /api/reports/status/{executionId}
     */
    @GetMapping("/status/{executionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteReport(@PathVariable Long id) {
        logger.info("Delete report: {}", id);
        
        boolean deleted = reportHistoryService.deleteReport(id);
        
        return ResponseEntity.ok(Map.of(
            "success", deleted,
            "message", deleted ? "Report deleted successfully" : "Failed to delete report"
        ));
    }

    /**
     * Download report file.
     * GET /api/reports/download/{id}?format=pdf|csv
     *
     * Reads the file that was written by ReportGenerationService.
     * Accepts an optional {@code format} param (pdf / csv); when supplied and it differs
     * from the stored file extension, the response content-type is overridden so the
     * browser prompts the right save-dialog.  The actual file bytes are served as-is.
     *
     * Returns:
     *   200  application/pdf  with Content-Disposition: attachment; filename=report-{id}.pdf
     *   200  text/csv         with Content-Disposition: attachment; filename=report-{id}.csv
     *   404  when the report file has not yet been produced or has been cleaned up
     */
    @GetMapping("/download/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "pdf") String format) {
        logger.info("Download report: {}, format: {}", id, format);

        ReportExecution execution = reportExecutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));

        pspIsolationService.validatePspAccess(execution.getPspId());

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(("Report is not ready. Status: " + execution.getStatus()).getBytes());
        }

        String filePath = execution.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Report file path is not recorded.".getBytes());
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Report file not found at: " + filePath).getBytes());
        }

        try {
            byte[] bytes = Files.readAllBytes(path);

            // Determine content-type from format param; fall back to stored file extension
            String resolvedFormat = format.toLowerCase();
            MediaType mediaType = switch (resolvedFormat) {
                case "csv"  -> MediaType.parseMediaType("text/csv");
                case "xml"  -> MediaType.APPLICATION_XML;
                default     -> MediaType.APPLICATION_PDF;
            };

            String reportCode = execution.getReport() != null
                    ? execution.getReport().getReportCode() : "report-" + id;
            String filename = reportCode + "-" + id + "." + resolvedFormat;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(bytes);

        } catch (Exception e) {
            logger.error("Failed to read report file: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Failed to read report file: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Schedule recurring report
     * POST /api/reports/schedule
     */
    @PostMapping("/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<ReportScheduleDTO> activateSchedule(@PathVariable Long scheduleId) {
        logger.info("Activate schedule: {}", scheduleId);
        
        ReportScheduleDTO schedule = reportSchedulingService.activateSchedule(scheduleId);
        return ResponseEntity.ok(schedule);
    }

    /**
     * Get chart data
     * POST /api/reports/chart
     *
     * Dispatches on {@code chartType} (case-insensitive):
     *   PIE / riskDistribution  — alert counts grouped by severity (INFO / WARN / CRITICAL)
     *   BAR / alertStatus       — alert counts grouped by status (open / closed / false_positive)
     *   LINE | BAR (default)    — daily transaction counts in the requested date window,
     *                             with a secondary dataset for daily amount totals (amount_cents / 100)
     *
     * Request fields used:
     *   reportType  — echoed back in the response
     *   chartType   — determines aggregation strategy (see above)
     *   options     — may carry { startDate, endDate, pspId } as a Map&lt;String,Object&gt;;
     *                 defaults to the last 30 days when absent
     */
    @PostMapping("/chart")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
    public ResponseEntity<ChartDataDTO> getChartData(@RequestBody ChartDataDTO request) {
        logger.info("Get chart data for report: {}", request.getReportType());
        return ResponseEntity.ok(buildChartData(request));
    }

    /**
     * Export chart data as CSV (or PDF summary).
     * GET /api/reports/chart/export?chartType=LINE&startDate=...&endDate=...&format=csv
     *
     * Re-uses the same aggregation logic as POST /chart.
     * CSV format: first column is the label (date / category), subsequent columns are
     * dataset values in the order they appear in the chart response.
     */
    @GetMapping("/chart/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportChartData(
            @RequestParam(defaultValue = "LINE") String chartType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long pspId,
            @RequestParam(defaultValue = "csv") String format) {

        logger.info("Export chart data: chartType={}, format={}", chartType, format);

        // Build a synthetic request object re-using the chart aggregation
        ChartDataDTO request = new ChartDataDTO();
        request.setChartType(chartType);
        request.setReportType(chartType.toLowerCase() + "_chart");

        Map<String, Object> options = new LinkedHashMap<>();
        if (startDate != null) options.put("startDate", startDate);
        if (endDate   != null) options.put("endDate",   endDate);
        if (pspId     != null) options.put("pspId",     pspId);
        request.setOptions(options);

        ChartDataDTO chartData = buildChartData(request);

        // Flatten chart data into rows for export
        @SuppressWarnings("unchecked")
        List<String> labels = chartData.getLabels() instanceof List
                ? (List<String>) chartData.getLabels() : List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> datasets = chartData.getDatasets() instanceof List
                ? (List<Map<String, Object>>) chartData.getDatasets() : List.of();

        // Build row data: [{label: "...", dataset1Label: value1, dataset2Label: value2, ...}]
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", labels.get(i));
            final int idx = i;
            for (Map<String, Object> ds : datasets) {
                String dsLabel = ds.get("label") != null ? ds.get("label").toString() : "value";
                Object dsData = ds.get("data");
                if (dsData instanceof List<?> list && idx < list.size()) {
                    row.put(dsLabel, list.get(idx));
                } else {
                    row.put(dsLabel, "");
                }
            }
            rows.add(row);
        }

        String title = chartType + " Chart Export";

        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdf = reportExportService.exportToPDF(rows, title);
            String filename = chartType.toLowerCase() + "-chart-export.pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        } else {
            byte[] csv = reportExportService.exportToCSV(rows);
            String filename = chartType.toLowerCase() + "-chart-export.csv";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(csv);
        }
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
    public ResponseEntity<List<ReportExecutionDTO>> getRecentReports(Authentication authentication,
                                                                          @RequestParam(defaultValue = "10") int limit) {
        User user = (User) authentication.getPrincipal();
        logger.debug("Get recent reports for user: {}", user.getId());

        List<ReportExecutionDTO> recent = reportHistoryService.getRecentReports(user.getId(), limit);
        return ResponseEntity.ok(recent);
    }

    /**
     * Get backend-aggregated transaction statistics.
     * GET /api/reports/transactions/stats
     *
     * Query params (all optional):
     *   pspId     — PSP to scope to; platform admin may omit for all-PSP totals.
     *   startDate — ISO date (yyyy-MM-dd) for window start; defaults to first day of current month.
     *   endDate   — ISO date (yyyy-MM-dd) for window end (exclusive); defaults to tomorrow.
     */
    @GetMapping("/transactions/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getTransactionStats(
            @RequestParam(required = false) Long pspId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);

        LocalDateTime start = (startDate != null)
                ? LocalDate.parse(startDate).atStartOfDay()
                : LocalDate.now().withDayOfMonth(1).atStartOfDay();

        LocalDateTime end = (endDate != null)
                ? LocalDate.parse(endDate).plusDays(1).atStartOfDay()
                : LocalDate.now().plusDays(1).atStartOfDay();

        Long repoFilter = (effectivePspId == null || effectivePspId == 0L) ? null : effectivePspId;

        long totalCount      = transactionRepository.countByPspAndPeriod(repoFilter, start, end);
        long approvedCount   = transactionRepository.countByPspAndDecisionAndPeriod(repoFilter, "APPROVED",      start, end);
        long declinedCount   = transactionRepository.countByPspAndDecisionAndPeriod(repoFilter, "DECLINED",      start, end);
        long manualCount     = transactionRepository.countByPspAndDecisionAndPeriod(repoFilter, "MANUAL_REVIEW", start, end);
        long highRiskCount   = transactionRepository.countByPspAndRiskLevelAndPeriod(repoFilter, "HIGH",   start, end);
        long mediumRiskCount = transactionRepository.countByPspAndRiskLevelAndPeriod(repoFilter, "MEDIUM", start, end);
        long lowRiskCount    = transactionRepository.countByPspAndRiskLevelAndPeriod(repoFilter, "LOW",    start, end);

        BigDecimal totalAmountCents   = transactionRepository.sumAmountByPspAndPeriod(repoFilter, start, end);
        BigDecimal averageAmountCents = (totalCount > 0)
                ? totalAmountCents.divide(BigDecimal.valueOf(totalCount), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long fraudAlertCount = alertRepository.countFraudAlertsByPspAndPeriod(repoFilter, start, end);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount",         totalCount);
        stats.put("approvedCount",      approvedCount);
        stats.put("declinedCount",      declinedCount);
        stats.put("manualReviewCount",  manualCount);
        stats.put("highRiskCount",      highRiskCount);
        stats.put("mediumRiskCount",    mediumRiskCount);
        stats.put("lowRiskCount",       lowRiskCount);
        stats.put("totalAmountCents",   totalAmountCents);
        stats.put("averageAmountCents", averageAmountCents);
        stats.put("fraudAlertCount",    fraudAlertCount);

        logger.debug("Transaction stats for psp={} [{} - {}]: total={}", repoFilter, start, end, totalCount);
        return ResponseEntity.ok(stats);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Shared chart-data aggregation used by both GET /chart (JSON) and GET /chart/export (bytes).
     */
    private ChartDataDTO buildChartData(ChartDataDTO request) {
        String chartType = request.getChartType() != null ? request.getChartType().toUpperCase() : "LINE";

        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate   = LocalDateTime.now();
        Long pspId = null;

        Object rawOptions = request.getOptions();
        if (rawOptions instanceof Map<?, ?> optMap) {
            Object rawStart = optMap.get("startDate");
            if (rawStart != null) {
                try { startDate = LocalDateTime.parse(rawStart.toString()); } catch (Exception ignored) {}
            }
            Object rawEnd = optMap.get("endDate");
            if (rawEnd != null) {
                try { endDate = LocalDateTime.parse(rawEnd.toString()); } catch (Exception ignored) {}
            }
            Object rawPsp = optMap.get("pspId");
            if (rawPsp instanceof Number) {
                pspId = ((Number) rawPsp).longValue();
            }
        }

        ChartDataDTO response = new ChartDataDTO();
        response.setReportType(request.getReportType());
        response.setChartType(request.getChartType());

        if ("PIE".equals(chartType) || "RISKDISTRIBUTION".equals(chartType)) {
            List<String> statuses = List.of("open", "closed", "false_positive");
            List<String> labels = new ArrayList<>();
            List<Long> data = new ArrayList<>();
            for (String st : statuses) {
                labels.add(st);
                data.add(alertRepository.countByStatus(st));
            }
            response.setLabels(labels);
            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("label", "Alert Risk Distribution");
            dataset.put("data", data);
            response.setDatasets(List.of(dataset));

        } else if ("ALERTSTATUS".equals(chartType)) {
            List<String> statuses = List.of("open", "closed", "false_positive");
            List<String> labels = new ArrayList<>();
            List<Long> data = new ArrayList<>();
            for (String st : statuses) {
                labels.add(st);
                data.add(alertRepository.countByStatus(st));
            }
            response.setLabels(labels);
            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("label", "Alerts by Status");
            dataset.put("data", data);
            response.setDatasets(List.of(dataset));

        } else {
            List<Object[]> rows = (pspId != null)
                    ? transactionRepository.getDailyTransactionCountByPspId(pspId, startDate, endDate)
                    : transactionRepository.getDailyTransactionCountAll(startDate, endDate);

            List<Object[]> amountRows = (pspId != null)
                    ? transactionRepository.getDailyTransactionVolumeByPspId(pspId, startDate, endDate)
                    : transactionRepository.getDailyTransactionVolumeAll(startDate, endDate);

            Map<String, Long> amountByDate = new LinkedHashMap<>();
            for (Object[] row : amountRows) {
                if (row[0] != null && row[1] != null) {
                    amountByDate.put(row[0].toString(), ((Number) row[1]).longValue());
                }
            }

            List<String> labels = new ArrayList<>();
            List<Long> countData = new ArrayList<>();
            List<Double> amountData = new ArrayList<>();

            for (Object[] row : rows) {
                if (row[0] == null || row[1] == null) continue;
                String dateLabel = row[0].toString();
                labels.add(dateLabel);
                countData.add(((Number) row[1]).longValue());
                long amountCents = amountByDate.getOrDefault(dateLabel, 0L);
                amountData.add(amountCents / 100.0);
            }

            response.setLabels(labels);

            Map<String, Object> countDataset = new LinkedHashMap<>();
            countDataset.put("label", "Transaction Count");
            countDataset.put("data", countData);

            Map<String, Object> amountDataset = new LinkedHashMap<>();
            amountDataset.put("label", "Transaction Volume");
            amountDataset.put("data", amountData);

            response.setDatasets(List.of(countDataset, amountDataset));
        }

        return response;
    }
}