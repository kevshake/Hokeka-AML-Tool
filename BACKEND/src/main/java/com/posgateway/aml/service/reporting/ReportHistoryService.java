package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.ReportExecutionDTO;
import com.posgateway.aml.dto.reporting.ReportHistoryFilterRequest;
import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.entity.reporting.ReportExecution;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Report History Service
 * Handles retrieval and management of report execution history
 */
@Service
public class ReportHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ReportHistoryService.class);

    private final ReportExecutionRepository reportExecutionRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;

    public ReportHistoryService(ReportExecutionRepository reportExecutionRepository,
                                UserRepository userRepository,
                                PspIsolationService pspIsolationService) {
        this.reportExecutionRepository = reportExecutionRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Get report history with filters and pagination
     */
    @Transactional(readOnly = true)
    public Page<ReportExecutionDTO> getReportHistory(Long pspId, ReportHistoryFilterRequest filters, Pageable pageable) {
        logger.debug("Getting report history for psp: {} with filters", pspId);
        
        // Sanitize PSP ID
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        // Apply filters
        Long reportId = filters != null ? filters.getReportId() : null;
        ExecutionStatus status = filters != null ? filters.getStatus() : null;
        LocalDateTime dateFrom = filters != null ? filters.getDateFrom() : null;
        LocalDateTime dateTo = filters != null ? filters.getDateTo() : null;
        
        // Use custom query with filters
        Page<ReportExecution> executions = reportExecutionRepository.findByFilters(
            reportId, effectivePspId, status, dateFrom, dateTo, pageable);
        
        return executions.map(this::convertToDTO);
    }

    /**
     * Get simplified report history list
     */
    @Transactional(readOnly = true)
    public Page<ReportExecutionDTO> getReportHistory(Long pspId, int page, int size, String sortBy, String sortDirection) {
        logger.debug("Getting report history for psp: {}, page: {}, size: {}", pspId, page, size);
        
        // Sanitize PSP ID
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        Sort sort = Sort.by(sortDirection.equalsIgnoreCase("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC, 
                             sortBy != null ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ReportExecution> executions;
        if (effectivePspId != null) {
            executions = reportExecutionRepository.findByPspId(effectivePspId, pageable);
        } else {
            executions = reportExecutionRepository.findAll(pageable);
        }
        
        return executions.map(this::convertToDTO);
    }

    /**
     * Get a specific report by ID
     */
    @Transactional(readOnly = true)
    public ReportExecutionDTO getReportById(Long reportId) {
        logger.debug("Getting report by ID: {}", reportId);
        
        ReportExecution execution = reportExecutionRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(execution.getPspId());
        
        return convertToDTO(execution);
    }

    /**
     * Get report by execution ID
     */
    @Transactional(readOnly = true)
    public ReportExecutionDTO getReportByExecutionId(String executionId) {
        logger.debug("Getting report by execution ID: {}", executionId);
        
        ReportExecution execution = reportExecutionRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(execution.getPspId());
        
        return convertToDTO(execution);
    }

    /**
     * Delete a report from history
     */
    @Transactional
    public boolean deleteReport(Long reportId) {
        logger.info("Deleting report: {}", reportId);
        
        ReportExecution execution = reportExecutionRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(execution.getPspId());
        
        // Delete associated file if exists
        if (execution.getFilePath() != null) {
            try {
                File file = new File(execution.getFilePath());
                if (file.exists()) {
                    file.delete();
                    logger.debug("Deleted report file: {}", execution.getFilePath());
                }
            } catch (Exception e) {
                logger.warn("Failed to delete report file: {}", execution.getFilePath(), e);
            }
        }
        
        reportExecutionRepository.delete(execution);
        logger.info("Report deleted: {}", reportId);
        
        return true;
    }

    /**
     * Download a report file
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadReport(Long reportId, String format) {
        logger.info("Downloading report: {} in format: {}", reportId, format);
        
        ReportExecution execution = reportExecutionRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(execution.getPspId());
        
        // Check if report is completed
        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("Report is not ready for download. Status: " + execution.getStatus());
        }
        
        // Determine file path
        String filePath = execution.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalStateException("Report file not found");
        }
        
        // Check if file exists
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Report file does not exist: " + filePath);
        }
        
        // Build filename
        String fileName = execution.getReport() != null 
            ? execution.getReport().getReportCode() + "_" + execution.getExecutionId()
            : execution.getExecutionId();
        
        String fileExtension = getFileExtension(filePath);
        if (format != null && !format.isEmpty()) {
            fileExtension = format.toLowerCase();
        }
        fileName = fileName + "." + fileExtension;
        
        // Return file as resource
        Resource resource = new FileSystemResource(path.toFile());
        
        MediaType mediaType = getMediaType(fileExtension);
        
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(resource);
    }

    /**
     * Get recent report executions for a user
     */
    @Transactional(readOnly = true)
    public List<ReportExecutionDTO> getRecentReports(Long userId, int limit) {
        logger.debug("Getting recent reports for user: {}, limit: {}", userId, limit);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReportExecution> executions = reportExecutionRepository.findByTriggeredBy(userId, pageable);
        
        return executions.getContent().stream()
            .filter(exec -> {
                try {
                    pspIsolationService.validatePspAccess(exec.getPspId());
                    return true;
                } catch (SecurityException e) {
                    return false;
                }
            })
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Retry a failed report execution
     */
    @Transactional
    public ReportExecutionDTO retryReport(Long reportId) {
        logger.info("Retrying report: {}", reportId);
        
        ReportExecution execution = reportExecutionRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(execution.getPspId());
        
        // Only failed reports can be retried
        if (execution.getStatus() != ExecutionStatus.FAILED) {
            throw new IllegalStateException("Only failed reports can be retried. Current status: " + execution.getStatus());
        }
        
        // Check retry limit
        if (execution.getRetryCount() != null && execution.getRetryCount() >= 3) {
            throw new IllegalStateException("Maximum retry attempts exceeded");
        }
        
        // Reset status for retry
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setProgressPercent(0);
        execution.setErrorMessage(null);
        execution.setRetryCount((execution.getRetryCount() != null ? execution.getRetryCount() : 0) + 1);
        execution.setStartedAt(null);
        execution.setCompletedAt(null);
        execution.setExecutionTimeMs(null);
        
        ReportExecution saved = reportExecutionRepository.save(execution);
        logger.info("Report queued for retry: {}", reportId);
        
        return convertToDTO(saved);
    }

    /**
     * Clean up old report files based on retention policy
     */
    @Transactional
    public int cleanupOldReports(int retentionDays) {
        logger.info("Cleaning up reports older than {} days", retentionDays);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        // Find old executions
        List<ReportExecution> oldExecutions = reportExecutionRepository.findStaleExecutions(
            ExecutionStatus.COMPLETED, cutoffDate);
        
        int deletedCount = 0;
        for (ReportExecution execution : oldExecutions) {
            try {
                // Delete file
                if (execution.getFilePath() != null) {
                    File file = new File(execution.getFilePath());
                    if (file.exists()) {
                        file.delete();
                    }
                }
                
                // Clear file reference
                execution.setFilePath(null);
                execution.setFileFormats(null);
                execution.setFileSizes(null);
                reportExecutionRepository.save(execution);
                
                deletedCount++;
            } catch (Exception e) {
                logger.warn("Failed to cleanup report: {}", execution.getId(), e);
            }
        }
        
        logger.info("Cleaned up {} old reports", deletedCount);
        return deletedCount;
    }

    /**
     * Get execution statistics
     */
    @Transactional(readOnly = true)
    public ExecutionStatistics getExecutionStatistics(Long pspId, LocalDateTime from, LocalDateTime to) {
        logger.debug("Getting execution statistics for psp: {}", pspId);
        
        // Sanitize PSP ID
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        ExecutionStatistics stats = new ExecutionStatistics();
        
        // This would require custom repository queries for efficient aggregation
        // For now, using a simplified approach
        Page<ReportExecution> executions;
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
        
        if (effectivePspId != null) {
            executions = reportExecutionRepository.findByPspId(effectivePspId, pageable);
        } else {
            executions = reportExecutionRepository.findAll(pageable);
        }
        
        List<ReportExecution> filtered = executions.getContent().stream()
            .filter(e -> (from == null || !e.getCreatedAt().isBefore(from)))
            .filter(e -> (to == null || !e.getCreatedAt().isAfter(to)))
            .toList();
        
        stats.setTotalExecutions(filtered.size());
        stats.setCompletedCount((int) filtered.stream().filter(e -> e.getStatus() == ExecutionStatus.COMPLETED).count());
        stats.setFailedCount((int) filtered.stream().filter(e -> e.getStatus() == ExecutionStatus.FAILED).count());
        stats.setPendingCount((int) filtered.stream().filter(e -> e.getStatus() == ExecutionStatus.PENDING).count());
        stats.setRunningCount((int) filtered.stream().filter(e -> e.getStatus() == ExecutionStatus.RUNNING).count());
        
        double avgTime = filtered.stream()
            .filter(e -> e.getExecutionTimeMs() != null)
            .mapToInt(ReportExecution::getExecutionTimeMs)
            .average()
            .orElse(0.0);
        stats.setAverageExecutionTimeMs((int) avgTime);
        
        long totalRecords = filtered.stream()
            .filter(e -> e.getTotalRecords() != null)
            .mapToLong(ReportExecution::getTotalRecords)
            .sum();
        stats.setTotalRecordsProcessed(totalRecords);
        
        return stats;
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

    /**
     * Get file extension from path
     */
    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.lastIndexOf('.') == -1) {
            return "bin";
        }
        return filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Get media type based on file extension
     */
    private MediaType getMediaType(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "xlsx", "xls" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xml" -> MediaType.APPLICATION_XML;
            case "json" -> MediaType.APPLICATION_JSON;
            case "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Execution Statistics DTO
     */
    public static class ExecutionStatistics {
        private int totalExecutions;
        private int completedCount;
        private int failedCount;
        private int pendingCount;
        private int runningCount;
        private int cancelledCount;
        private int averageExecutionTimeMs;
        private long totalRecordsProcessed;

        // Getters and Setters
        public int getTotalExecutions() { return totalExecutions; }
        public void setTotalExecutions(int totalExecutions) { this.totalExecutions = totalExecutions; }

        public int getCompletedCount() { return completedCount; }
        public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }

        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

        public int getRunningCount() { return runningCount; }
        public void setRunningCount(int runningCount) { this.runningCount = runningCount; }

        public int getCancelledCount() { return cancelledCount; }
        public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }

        public int getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
        public void setAverageExecutionTimeMs(int averageExecutionTimeMs) { this.averageExecutionTimeMs = averageExecutionTimeMs; }

        public long getTotalRecordsProcessed() { return totalRecordsProcessed; }
        public void setTotalRecordsProcessed(long totalRecordsProcessed) { this.totalRecordsProcessed = totalRecordsProcessed; }
    }
}
