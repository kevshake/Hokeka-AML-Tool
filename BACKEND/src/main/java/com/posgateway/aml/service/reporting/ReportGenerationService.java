package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.*;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.reporting.ReportDefinitionRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Report Generation Service
 * Handles report generation, preview, execution tracking, and cancellation
 */
@Service
public class ReportGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationService.class);

    private final ReportRepository reportRepository;
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;
    private final EntityManager entityManager;
    private final ReportExportService reportExportService;

    // In-memory tracking for cancellations
    private final Set<String> cancellationTokens = ConcurrentHashMap.newKeySet();

    public ReportGenerationService(ReportRepository reportRepository,
                                   ReportDefinitionRepository reportDefinitionRepository,
                                   ReportExecutionRepository reportExecutionRepository,
                                   UserRepository userRepository,
                                   PspIsolationService pspIsolationService,
                                   EntityManager entityManager,
                                   ReportExportService reportExportService) {
        this.reportRepository = reportRepository;
        this.reportDefinitionRepository = reportDefinitionRepository;
        this.reportExecutionRepository = reportExecutionRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
        this.entityManager = entityManager;
        this.reportExportService = reportExportService;
    }

    /**
     * Generate a report asynchronously
     */
    @Async("taskExecutor")
    @Transactional
    public CompletableFuture<ReportExecutionDTO> generateReport(String reportType, 
                                                                   Map<String, Object> parameters,
                                                                   Long userId, 
                                                                   Long pspId) {
        logger.info("Starting report generation for type: {}, user: {}, psp: {}", reportType, userId, pspId);
        
        long startTime = System.currentTimeMillis();
        String executionId = generateExecutionId();
        
        try {
            // Find report by code
            Report report = reportRepository.findByReportCode(reportType)
                .orElseThrow(() -> new IllegalArgumentException("Report type not found: " + reportType));
            
            // Get active definition
            ReportDefinition definition = reportDefinitionRepository
                .findByReportIdAndIsActiveTrue(report.getId())
                .orElseThrow(() -> new IllegalStateException("No active definition found for report: " + reportType));
            
            // Sanitize PSP ID based on user permissions
            Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
            
            // Create execution record
            ReportExecution execution = new ReportExecution();
            execution.setReport(report);
            execution.setExecutionId(executionId);
            execution.setPspId(effectivePspId);
            execution.setTriggeredBy(userId);
            execution.setTriggerType(TriggerType.MANUAL);
            execution.setParameters(parameters);
            execution.setFiltersApplied(parameters != null ? parameters.get("filters") instanceof Map ? 
                (Map<String, Object>) parameters.get("filters") : null : null);
            execution.setStatus(ExecutionStatus.RUNNING);
            execution.setProgressPercent(0);
            execution.setStartedAt(LocalDateTime.now());
            
            if (parameters != null) {
                if (parameters.get("dateFrom") instanceof String) {
                    execution.setDateFrom(LocalDateTime.parse((String) parameters.get("dateFrom")));
                }
                if (parameters.get("dateTo") instanceof String) {
                    execution.setDateTo(LocalDateTime.parse((String) parameters.get("dateTo")));
                }
            }
            
            execution = reportExecutionRepository.save(execution);
            
            // Check for cancellation
            if (cancellationTokens.contains(executionId)) {
                execution.setStatus(ExecutionStatus.CANCELLED);
                execution.setCompletedAt(LocalDateTime.now());
                reportExecutionRepository.save(execution);
                cancellationTokens.remove(executionId);
                logger.info("Report generation cancelled: {}", executionId);
                return CompletableFuture.completedFuture(convertToDTO(execution));
            }
            
            // Execute the report query
            List<Map<String, Object>> results = executeReportQuery(definition, parameters, effectivePspId);
            
            // Update progress
            execution.setProgressPercent(50);
            execution.setTotalRecords((long) results.size());
            reportExecutionRepository.save(execution);
            
            // Check for cancellation again
            if (cancellationTokens.contains(executionId)) {
                execution.setStatus(ExecutionStatus.CANCELLED);
                execution.setCompletedAt(LocalDateTime.now());
                reportExecutionRepository.save(execution);
                cancellationTokens.remove(executionId);
                logger.info("Report generation cancelled: {}", executionId);
                return CompletableFuture.completedFuture(convertToDTO(execution));
            }
            
            // Export results
            String outputFormat = parameters != null && parameters.get("outputFormat") != null 
                ? (String) parameters.get("outputFormat") 
                : "PDF";
            String filePath = exportReport(results, report, outputFormat, executionId);
            
            // Complete execution
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setProgressPercent(100);
            execution.setFilePath(filePath);
            execution.setFileFormats(List.of(outputFormat));
            execution.setCompletedAt(LocalDateTime.now());
            execution.setExecutionTimeMs((int) (System.currentTimeMillis() - startTime));
            
            ReportExecution saved = reportExecutionRepository.save(execution);
            logger.info("Report generation completed: {} in {}ms", executionId, execution.getExecutionTimeMs());
            
            return CompletableFuture.completedFuture(convertToDTO(saved));
            
        } catch (Exception e) {
            logger.error("Report generation failed for execution: {}", executionId, e);
            
            // Create failed execution record if not already created
            ReportExecution failedExecution = reportExecutionRepository.findByExecutionId(executionId)
                .orElseGet(() -> {
                    ReportExecution exec = new ReportExecution();
                    exec.setExecutionId(executionId);
                    exec.setTriggeredBy(userId);
                    exec.setPspId(pspId);
                    exec.setStatus(ExecutionStatus.FAILED);
                    return exec;
                });
            
            failedExecution.setStatus(ExecutionStatus.FAILED);
            failedExecution.setErrorMessage(e.getMessage());
            failedExecution.setCompletedAt(LocalDateTime.now());
            failedExecution.setExecutionTimeMs((int) (System.currentTimeMillis() - startTime));
            reportExecutionRepository.save(failedExecution);
            
            return CompletableFuture.completedFuture(convertToDTO(failedExecution));
        }
    }

    /**
     * Preview report data (returns sample data without full execution)
     */
    @Transactional(readOnly = true)
    public ReportPreviewDTO previewReport(String reportType, Map<String, Object> parameters, Long pspId) {
        logger.info("Generating report preview for type: {}, psp: {}", reportType, pspId);
        
        try {
            // Find report by code
            Report report = reportRepository.findByReportCode(reportType)
                .orElseThrow(() -> new IllegalArgumentException("Report type not found: " + reportType));
            
            // Get active definition
            ReportDefinition definition = reportDefinitionRepository
                .findByReportIdAndIsActiveTrue(report.getId())
                .orElseThrow(() -> new IllegalStateException("No active definition found for report: " + reportType));
            
            // Sanitize PSP ID
            Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
            
            // Execute limited query for preview
            String previewQuery = buildPreviewQuery(definition.getSqlQuery());
            List<Map<String, Object>> results = executeDynamicQuery(previewQuery, parameters, effectivePspId, 100);
            
            // Get total count
            Long totalCount = 0L;
            if (definition.getCountQuery() != null && !definition.getCountQuery().isEmpty()) {
                totalCount = executeCountQuery(definition.getCountQuery(), parameters, effectivePspId);
            } else {
                totalCount = (long) results.size();
            }
            
            ReportPreviewDTO preview = new ReportPreviewDTO();
            preview.setReportType(reportType);
            preview.setReportName(report.getReportName());
            preview.setColumns(definition.getColumns());
            preview.setData(results);
            preview.setTotalCount(totalCount);
            preview.setHasMore(totalCount > results.size());
            preview.setSampleQuery(previewQuery);
            
            return preview;
            
        } catch (Exception e) {
            logger.error("Report preview failed for type: {}", reportType, e);
            throw new RuntimeException("Failed to generate report preview: " + e.getMessage(), e);
        }
    }

    /**
     * Get report execution status
     */
    @Transactional(readOnly = true)
    public ReportExecutionDTO getReportExecutionStatus(String executionId) {
        logger.debug("Getting execution status for: {}", executionId);
        
        ReportExecution execution = reportExecutionRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(execution.getPspId());
        
        return convertToDTO(execution);
    }

    /**
     * Cancel a running report execution
     */
    @Transactional
    public boolean cancelReportExecution(String executionId) {
        logger.info("Cancelling report execution: {}", executionId);
        
        ReportExecution execution = reportExecutionRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(execution.getPspId());
        
        // Can only cancel pending or running executions
        if (execution.getStatus() != ExecutionStatus.PENDING && execution.getStatus() != ExecutionStatus.RUNNING) {
            logger.warn("Cannot cancel execution {} - status is {}", executionId, execution.getStatus());
            return false;
        }
        
        // Add to cancellation tokens
        cancellationTokens.add(executionId);
        
        // Update status
        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setCompletedAt(LocalDateTime.now());
        reportExecutionRepository.save(execution);
        
        logger.info("Report execution cancelled: {}", executionId);
        return true;
    }

    /**
     * Execute the report query with parameters
     */
    private List<Map<String, Object>> executeReportQuery(ReportDefinition definition, 
                                                          Map<String, Object> parameters,
                                                          Long pspId) {
        String sql = definition.getSqlQuery();
        return executeDynamicQuery(sql, parameters, pspId, null);
    }

    /**
     * Execute a dynamic SQL query with parameter substitution
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeDynamicQuery(String sql, Map<String, Object> parameters, 
                                                           Long pspId, Integer limit) {
        try {
            // Build query with PSP isolation
            String modifiedSql = applyPspIsolation(sql, pspId);
            
            // Apply limit if specified
            if (limit != null && limit > 0) {
                modifiedSql = modifiedSql + " LIMIT " + limit;
            }
            
            Query query = entityManager.createNativeQuery(modifiedSql);
            
            // Set parameters if provided
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    if (!entry.getKey().equals("filters") && !entry.getKey().equals("outputFormat")) {
                        try {
                            query.setParameter(entry.getKey(), entry.getValue());
                        } catch (IllegalArgumentException e) {
                            // Parameter not found in query, skip
                        }
                    }
                }
            }
            
            // Execute and convert results
            List<Object[]> results = query.getResultList();
            return convertToMapList(results, extractColumnNames(modifiedSql));
            
        } catch (Exception e) {
            logger.error("Query execution failed: {}", sql, e);
            throw new RuntimeException("Query execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute count query
     */
    private Long executeCountQuery(String countQuery, Map<String, Object> parameters, Long pspId) {
        try {
            String modifiedSql = applyPspIsolation(countQuery, pspId);
            Query query = entityManager.createNativeQuery(modifiedSql);
            
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    if (!entry.getKey().equals("filters") && !entry.getKey().equals("outputFormat")) {
                        try {
                            query.setParameter(entry.getKey(), entry.getValue());
                        } catch (IllegalArgumentException e) {
                            // Skip
                        }
                    }
                }
            }
            
            Number result = (Number) query.getSingleResult();
            return result != null ? result.longValue() : 0L;
            
        } catch (Exception e) {
            logger.warn("Count query failed: {}", countQuery, e);
            return 0L;
        }
    }

    /**
     * Apply PSP isolation to SQL query
     */
    private String applyPspIsolation(String sql, Long pspId) {
        if (pspId == null || pspIsolationService.isPlatformAdministrator()) {
            return sql;
        }
        
        // Add PSP filter if query contains WHERE clause
        if (sql.toLowerCase().contains("where")) {
            return sql.replaceAll("(?i)where", "WHERE psp_id = " + pspId + " AND ");
        } else {
            // Add WHERE clause before ORDER BY, GROUP BY, or at the end
            if (sql.toLowerCase().contains("order by")) {
                return sql.replaceAll("(?i)order by", "WHERE psp_id = " + pspId + " ORDER BY");
            } else if (sql.toLowerCase().contains("group by")) {
                return sql.replaceAll("(?i)group by", "WHERE psp_id = " + pspId + " GROUP BY");
            } else {
                return sql + " WHERE psp_id = " + pspId;
            }
        }
    }

    /**
     * Build preview query with limit
     */
    private String buildPreviewQuery(String sql) {
        // Remove any existing limit
        return sql.replaceAll("(?i)LIMIT\\s+\\d+", "").trim();
    }

    /**
     * Extract column names from SQL query (simplified)
     */
    private List<String> extractColumnNames(String sql) {
        // Simplified column extraction - in production, use ResultSet metadata
        List<String> columns = new ArrayList<>();
        String lowerSql = sql.toLowerCase();
        
        int selectIdx = lowerSql.indexOf("select");
        int fromIdx = lowerSql.indexOf("from");
        
        if (selectIdx >= 0 && fromIdx > selectIdx) {
            String cols = sql.substring(selectIdx + 6, fromIdx);
            String[] parts = cols.split(",");
            for (String part : parts) {
                String col = part.trim();
                // Extract alias if present
                int asIdx = col.toLowerCase().indexOf(" as ");
                if (asIdx > 0) {
                    col = col.substring(asIdx + 4).trim();
                }
                // Extract last part of qualified name
                int dotIdx = col.lastIndexOf('.');
                if (dotIdx >= 0) {
                    col = col.substring(dotIdx + 1);
                }
                columns.add(col);
            }
        }
        
        return columns;
    }

    /**
     * Convert query results to map list
     */
    private List<Map<String, Object>> convertToMapList(List<Object[]> results, List<String> columnNames) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        
        for (Object[] row : results) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < row.length && i < columnNames.size(); i++) {
                map.put(columnNames.get(i), row[i]);
            }
            mapList.add(map);
        }
        
        return mapList;
    }

    /**
     * Export report to file
     */
    private String exportReport(List<Map<String, Object>> data, Report report, String format, String executionId) {
        String fileName = report.getReportCode() + "_" + executionId + "." + format.toLowerCase();
        String filePath = "/tmp/reports/" + fileName;
        
        try {
            switch (format.toUpperCase()) {
                case "PDF":
                    return reportExportService.exportToPDF(data, report.getReportName(), filePath);
                case "CSV":
                    return reportExportService.exportToCSV(data, filePath);
                case "EXCEL":
                case "XLSX":
                    return reportExportService.exportToExcel(data, report.getReportName(), filePath);
                case "XML":
                    return reportExportService.exportToXML(data, report.getReportName(), filePath);
                default:
                    return reportExportService.exportToPDF(data, report.getReportName(), filePath);
            }
        } catch (Exception e) {
            logger.error("Export failed for format: {}", format, e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate unique execution ID
     */
    private String generateExecutionId() {
        return "EXEC_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Convert entity to DTO
     */
    private ReportExecutionDTO convertToDTO(ReportExecution execution) {
        ReportExecutionDTO dto = new ReportExecutionDTO();
        dto.setId(execution.getId());
        dto.setExecutionId(execution.getExecutionId());
        dto.setReportId(execution.getReport() != null ? execution.getReport().getId() : null);
        dto.setReportName(execution.getReport() != null ? execution.getReport().getReportName() : null);
        dto.setReportCode(execution.getReport() != null ? execution.getReport().getReportCode() : null);
        dto.setPspId(execution.getPspId());
        dto.setTriggeredBy(execution.getTriggeredBy());
        
        if (execution.getTriggeredBy() != null) {
            userRepository.findById(execution.getTriggeredBy())
                .ifPresent(user -> dto.setTriggeredByName(user.getFullName()));
        }
        
        dto.setTriggerType(execution.getTriggerType());
        dto.setParameters(execution.getParameters());
        dto.setDateFrom(execution.getDateFrom());
        dto.setDateTo(execution.getDateTo());
        dto.setFiltersApplied(execution.getFiltersApplied());
        dto.setStatus(execution.getStatus());
        dto.setProgressPercent(execution.getProgressPercent());
        dto.setTotalRecords(execution.getTotalRecords());
        dto.setFilePath(execution.getFilePath());
        dto.setFileFormats(execution.getFileFormats());
        dto.setFileSizes(execution.getFileSizes());
        dto.setStartedAt(execution.getStartedAt());
        dto.setCompletedAt(execution.getCompletedAt());
        dto.setExecutionTimeMs(execution.getExecutionTimeMs());
        dto.setErrorMessage(execution.getErrorMessage());
        dto.setRetryCount(execution.getRetryCount());
        dto.setCreatedAt(execution.getCreatedAt());
        
        return dto;
    }
}
