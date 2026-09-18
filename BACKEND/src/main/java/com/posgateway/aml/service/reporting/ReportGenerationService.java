package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.*;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.reporting.ReportDefinitionRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportResultRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.service.record.RecordTrailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
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
    private final ReportResultRepository reportResultRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;
    private final EntityManager entityManager;
    private final ReportExportService reportExportService;
    private final RecordTrailService recordTrailService;

    // In-memory tracking for cancellations
    private final Set<String> cancellationTokens = ConcurrentHashMap.newKeySet();

    /**
     * Validate a request and persist its pollable execution row before the async worker starts.
     * This prevents clients from polling a synthetic PENDING state forever when the report code
     * or active definition is invalid.
     */
    @Transactional
    public ReportExecutionDTO queueReport(String executionId, String reportType,
                                          Map<String, Object> parameters, Long userId, Long pspId) {
        return queueReportInternal(executionId, reportType, parameters, userId,
                pspIsolationService.sanitizePspId(pspId), TriggerType.MANUAL);
    }

    @Transactional
    public ReportExecutionDTO queueScheduledReport(String executionId, String reportType,
                                                    Map<String, Object> parameters, Long userId, Long pspId) {
        if (pspId == null) throw new IllegalArgumentException("Scheduled report must belong to a PSP");
        return queueReportInternal(executionId, reportType, parameters, userId, pspId, TriggerType.SCHEDULED);
    }

    private ReportExecutionDTO queueReportInternal(String executionId, String reportType,
                                                    Map<String, Object> parameters, Long userId,
                                                    Long effectivePspId, TriggerType triggerType) {
        Report report = reportRepository.findByReportCode(reportType)
                .orElseThrow(() -> new IllegalArgumentException("Report type not found: " + reportType));
        reportDefinitionRepository.findByReportIdAndIsActiveTrue(report.getId())
                .orElseThrow(() -> new IllegalStateException("No active definition found for report: " + reportType));
        ReportExecution execution = new ReportExecution();
        execution.setReport(report);
        execution.setExecutionId(executionId);
        execution.setPspId(effectivePspId);
        execution.setTriggeredBy(userId);
        execution.setTriggerType(triggerType);
        execution.setParameters(parameters);
        execution.setSourceContext(sourceContext(parameters));
        execution.setFiltersApplied(parameters != null && parameters.get("filters") instanceof Map<?, ?> filters
                ? new LinkedHashMap<>((Map<String, Object>) filters) : null);
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setProgressPercent(0);
        setExecutionDates(execution, parameters);
        return convertToDTO(reportExecutionRepository.save(execution));
    }

    public ReportGenerationService(ReportRepository reportRepository,
                                   ReportDefinitionRepository reportDefinitionRepository,
                                   ReportExecutionRepository reportExecutionRepository,
                                   UserRepository userRepository,
                                   PspIsolationService pspIsolationService,
                                   EntityManager entityManager,
                                   ReportExportService reportExportService) {
        this(reportRepository, reportDefinitionRepository, reportExecutionRepository, userRepository,
                pspIsolationService, entityManager, reportExportService, null, null);
    }

    public ReportGenerationService(ReportRepository reportRepository,
                                   ReportDefinitionRepository reportDefinitionRepository,
                                   ReportExecutionRepository reportExecutionRepository,
                                   UserRepository userRepository,
                                   PspIsolationService pspIsolationService,
                                   EntityManager entityManager,
                                   ReportExportService reportExportService,
                                   RecordTrailService recordTrailService) {
        this(reportRepository, reportDefinitionRepository, reportExecutionRepository, userRepository,
                pspIsolationService, entityManager, reportExportService, recordTrailService, null);
    }

    @Autowired
    public ReportGenerationService(ReportRepository reportRepository,
                                   ReportDefinitionRepository reportDefinitionRepository,
                                   ReportExecutionRepository reportExecutionRepository,
                                   UserRepository userRepository,
                                   PspIsolationService pspIsolationService,
                                   EntityManager entityManager,
                                   ReportExportService reportExportService,
                                   RecordTrailService recordTrailService,
                                   ReportResultRepository reportResultRepository) {
        this.reportRepository = reportRepository;
        this.reportDefinitionRepository = reportDefinitionRepository;
        this.reportExecutionRepository = reportExecutionRepository;
        this.reportResultRepository = reportResultRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
        this.entityManager = entityManager;
        this.reportExportService = reportExportService;
        this.recordTrailService = recordTrailService;
    }

    /**
     * Generate a report asynchronously
     */
    // backgroundTaskExecutor is the dedicated long-running pool defined in
    // AsyncConfig. (The previous "taskExecutor" qualifier matched no bean — the
    // high-throughput pool is registered as "amlTaskExecutor" — so every report
    // generation request failed with NoSuchBeanDefinitionException.)
    @Async("backgroundTaskExecutor")
    @Transactional
    public CompletableFuture<ReportExecutionDTO> generateReport(String executionId,
                                                                   String reportType,
                                                                   Map<String, Object> parameters,
                                                                   Long userId,
                                                                   Long pspId) {
        return generateReportInternal(executionId, reportType, parameters, userId,
                pspIsolationService.sanitizePspId(pspId), TriggerType.MANUAL);
    }

    @Async("backgroundTaskExecutor")
    @Transactional
    public CompletableFuture<ReportExecutionDTO> generateScheduledReport(String executionId,
                                                                          String reportType,
                                                                          Map<String, Object> parameters,
                                                                          Long userId,
                                                                          Long pspId) {
        if (pspId == null) throw new IllegalArgumentException("Scheduled report must belong to a PSP");
        return generateReportInternal(executionId, reportType, parameters, userId, pspId, TriggerType.SCHEDULED);
    }

    private CompletableFuture<ReportExecutionDTO> generateReportInternal(String executionId,
                                                                          String reportType,
                                                                          Map<String, Object> parameters,
                                                                          Long userId,
                                                                          Long effectivePspId,
                                                                          TriggerType triggerType) {
        logger.info("Starting report generation for type: {}, execution: {}, user: {}, psp: {}",
                reportType, executionId, userId, effectivePspId);

        long startTime = System.currentTimeMillis();
        
        try {
            // Find report by code
            Report report = reportRepository.findByReportCode(reportType)
                .orElseThrow(() -> new IllegalArgumentException("Report type not found: " + reportType));
            
            // Get active definition
            ReportDefinition definition = reportDefinitionRepository
                .findByReportIdAndIsActiveTrue(report.getId())
                .orElseThrow(() -> new IllegalStateException("No active definition found for report: " + reportType));
            
            // The controller queues a durable PENDING row before invoking this worker. Keep a
            // direct-call fallback for scheduled/internal callers that use the service itself.
            ReportExecution execution = reportExecutionRepository.findByExecutionId(executionId)
                    .orElseGet(ReportExecution::new);
            execution.setReport(report);
            execution.setExecutionId(executionId);
            execution.setPspId(effectivePspId);
            execution.setTriggeredBy(userId);
            execution.setTriggerType(triggerType);
            execution.setParameters(parameters);
            execution.setSourceContext(sourceContext(parameters));
            execution.setFiltersApplied(parameters != null && parameters.get("filters") instanceof Map<?, ?> filters
                    ? new LinkedHashMap<>((Map<String, Object>) filters) : null);
            execution.setStatus(ExecutionStatus.RUNNING);
            execution.setProgressPercent(0);
            execution.setStartedAt(LocalDateTime.now());
            setExecutionDates(execution, parameters);
            
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
            ReportTrace trace = reportTrace(results, parameters);
            execution.setCalculationSummary(trace.calculationSummary());
            execution.setRecordLinks(trace.recordLinks());
            persistResultRows(execution.getId(), results);
            
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
            String outputFormat = canonicalOutputFormat(parameters != null
                    ? parameters.get("outputFormat") : null);
            String filePath = exportReport(results, report, outputFormat, executionId);
            
            // Complete execution
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setProgressPercent(100);
            execution.setFilePath(filePath);
            execution.setFileFormats(List.of(outputFormat));
            try {
                execution.setFileSizes(Map.of(outputFormat,
                        java.nio.file.Files.size(java.nio.file.Path.of(filePath))));
            } catch (java.io.IOException e) {
                throw new RuntimeException("Unable to record report file size", e);
            }
            execution.setCompletedAt(LocalDateTime.now());
            execution.setExecutionTimeMs((int) (System.currentTimeMillis() - startTime));
            
            ReportExecution saved = reportExecutionRepository.save(execution);
            logger.info("Report generation completed: {} in {}ms", executionId, execution.getExecutionTimeMs());
            
            return CompletableFuture.completedFuture(convertToDTO(saved));
            
        } catch (Exception e) {
            logger.error("Report generation failed for execution: {}", executionId, e);
            
            // Create failed execution record if not already created
            Optional<ReportExecution> existing = reportExecutionRepository.findByExecutionId(executionId);
            if (existing.isEmpty()) {
                ReportExecutionDTO failed = new ReportExecutionDTO();
                failed.setExecutionId(executionId);
                failed.setTriggeredBy(userId);
                failed.setPspId(effectivePspId);
                failed.setStatus(ExecutionStatus.FAILED);
                failed.setErrorMessage(e.getMessage());
                failed.setCompletedAt(LocalDateTime.now());
                failed.setExecutionTimeMs((int) (System.currentTimeMillis() - startTime));
                return CompletableFuture.completedFuture(failed);
            }
            ReportExecution failedExecution = existing.get();
            failedExecution.setStatus(ExecutionStatus.FAILED);
            failedExecution.setErrorMessage(e.getMessage());
            failedExecution.setCompletedAt(LocalDateTime.now());
            failedExecution.setExecutionTimeMs((int) (System.currentTimeMillis() - startTime));
            reportExecutionRepository.save(failedExecution);
            
            return CompletableFuture.completedFuture(convertToDTO(failedExecution));
        }
    }

    /**
     * Preview the first 100 rows from the real report query without exporting a file.
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

        // The controller hands the executionId to the client before the async
        // worker persists the row — report PENDING until it appears.
        java.util.Optional<ReportExecution> maybe = reportExecutionRepository.findByExecutionId(executionId);
        if (maybe.isEmpty()) {
            ReportExecutionDTO pending = new ReportExecutionDTO();
            pending.setExecutionId(executionId);
            pending.setStatus(ExecutionStatus.PENDING);
            pending.setProgressPercent(0);
            return pending;
        }
        ReportExecution execution = maybe.get();

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

    private Map<String, Object> sourceContext(Map<String, Object> parameters) {
        Map<String, Object> source = new LinkedHashMap<>();
        if (parameters == null) return source;
        copyIfPresent(parameters, source, "recordType");
        copyIfPresent(parameters, source, "recordId");
        copyIfPresent(parameters, source, "dateFrom");
        copyIfPresent(parameters, source, "dateTo");
        if (parameters.get("filters") instanceof Map<?, ?> filters) {
            source.put("filters", new LinkedHashMap<>((Map<String, Object>) filters));
        }
        Object type = source.get("recordType");
        Object id = source.get("recordId");
        if (recordTrailService != null && type != null && id != null) {
            recordTrailService.resolveLink(String.valueOf(type), id).ifPresent(link -> source.put("sourceRecord", linkToMap(link)));
        }
        return source;
    }

    private ReportTrace reportTrace(List<Map<String, Object>> rows, Map<String, Object> parameters) {
        Map<String, BigDecimal> totals = new TreeMap<>();
        Map<String, Map<String, Object>> deduplicatedLinks = new LinkedHashMap<>();
        if (parameters != null && parameters.get("recordType") != null && parameters.get("recordId") != null) {
            addRecordLink(deduplicatedLinks, String.valueOf(parameters.get("recordType")), parameters.get("recordId"), "REPORT_SOURCE");
        }
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    totals.merge(entry.getKey(), new BigDecimal(number.toString()), BigDecimal::add);
                }
                recordTypeForColumn(entry.getKey()).ifPresent(type -> addRecordLink(deduplicatedLinks, type, entry.getValue(), "REPORT_ROW"));
            }
        }
        Map<String, Object> calculationSummary = new LinkedHashMap<>();
        calculationSummary.put("rowCount", rows.size());
        calculationSummary.put("numericTotals", totals);
        calculationSummary.put("calculatedAt", LocalDateTime.now());
        return new ReportTrace(calculationSummary, List.copyOf(deduplicatedLinks.values()));
    }

    private Optional<String> recordTypeForColumn(String column) {
        String normalized = column == null ? "" : column.replace("_", "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "alertid" -> Optional.of("ALERT");
            case "caseid" -> Optional.of("CASE");
            case "txnid", "transactionid" -> Optional.of("TRANSACTION");
            case "merchantid" -> Optional.of("MERCHANT");
            case "multiassetcustomerid" -> Optional.of("MULTI_ASSET_CUSTOMER");
            case "multiassettransactionid" -> Optional.of("MULTI_ASSET_TRANSACTION");
            case "multiassetrisksignalid" -> Optional.of("MULTI_ASSET_RISK_SIGNAL");
            case "mobilemoneytransactioncontextid" -> Optional.of("MOBILE_MONEY_TRANSACTION_CONTEXT");
            case "mobilemoneyriskprofileid" -> Optional.of("MOBILE_MONEY_RISK_PROFILE");
            case "mobilemoneynetworkedgeid" -> Optional.of("MOBILE_MONEY_NETWORK_EDGE");
            case "vaspdirectoryentryid", "originatorvaspdirectoryentryid", "beneficiaryvaspdirectoryentryid" -> Optional.of("VASP_DIRECTORY_ENTRY");
            case "cryptowalletprofileid" -> Optional.of("CRYPTO_WALLET_PROFILE");
            case "walletscreeningrecordid" -> Optional.of("WALLET_SCREENING_RECORD");
            case "vaspscreeningrecordid" -> Optional.of("VASP_SCREENING_RECORD");
            case "travelrulejurisdictionpolicyid" -> Optional.of("TRAVEL_RULE_JURISDICTION_POLICY");
            case "travelruletransferid" -> Optional.of("TRAVEL_RULE_TRANSFER");
            case "virtualassetregulatoraccessgrantid" -> Optional.of("VIRTUAL_ASSET_REGULATOR_ACCESS_GRANT");
            case "virtualassetregulatoraccesslogid" -> Optional.of("VIRTUAL_ASSET_REGULATOR_ACCESS_LOG");
            case "marketsurveillancesignalid" -> Optional.of("MARKET_SURVEILLANCE_SIGNAL");
            case "marketorderid" -> Optional.of("MARKET_ORDER");
            case "marketexecutionid" -> Optional.of("MARKET_EXECUTION");
            case "corporateintelligencecheckid" -> Optional.of("CORPORATE_INTELLIGENCE_CHECK");
            case "fixmessageeventid" -> Optional.of("FIX_MESSAGE_EVENT");
            case "ruleversionid" -> Optional.of("RULE_VERSION");
            case "executionid", "reportexecutionid" -> Optional.of("REPORT_EXECUTION");
            case "scheduleid", "reportscheduleid" -> Optional.of("REPORT_SCHEDULE");
            case "chargebackdisputeid", "disputeid" -> Optional.of("CHARGEBACK_DISPUTE");
            case "merchantdocumentid", "documentid" -> Optional.of("MERCHANT_DOCUMENT");
            case "regulatorysubmissionid", "submissionid" -> Optional.of("REGULATORY_SUBMISSION");
            case "sarid", "suspiciousactivityreportid" -> Optional.of("SUSPICIOUS_ACTIVITY_REPORT");
            case "ruledefinitionid" -> Optional.of("RULE_DEFINITION");
            case "velocityruleid" -> Optional.of("VELOCITY_RULE");
            case "riskthresholdid" -> Optional.of("RISK_THRESHOLD");
            case "userid", "preparedbyid", "reviewedbyid", "approvedbyid", "filedbyid" -> Optional.of("USER");
            case "pspid" -> Optional.of("PSP");
            case "reportid" -> Optional.of("REPORT");
            case "invoiceid" -> Optional.of("INVOICE");
            case "subscriptionid" -> Optional.of("SUBSCRIPTION");
            case "cbksubmissionid" -> Optional.of("CBK_SUBMISSION");
            case "auditlogid" -> Optional.of("AUDIT_LOG");
            case "roleid" -> Optional.of("ROLE");
            default -> Optional.empty();
        };
    }

    private void addRecordLink(Map<String, Map<String, Object>> links, String type, Object id, String relationship) {
        if (id == null || String.valueOf(id).isBlank() || links.size() >= 500) return;
        if (recordTrailService != null) {
            recordTrailService.resolveLink(type, id).ifPresent(link -> {
                Map<String, Object> value = linkToMap(link);
                value.put("relationship", relationship);
                links.putIfAbsent(type + ":" + id, value);
            });
            return;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("recordType", type);
        value.put("recordId", String.valueOf(id));
        value.put("relationship", relationship);
        links.putIfAbsent(type + ":" + id, value);
    }

    private Map<String, Object> linkToMap(com.posgateway.aml.dto.record.RecordTrailDtos.RecordLink link) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("recordType", link.recordType());
        value.put("recordId", link.recordId());
        value.put("label", link.label());
        value.put("relationship", link.relationship());
        value.put("summary", link.summary());
        return value;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) target.put(key, source.get(key));
    }

    private record ReportTrace(Map<String, Object> calculationSummary, List<Map<String, Object>> recordLinks) {}

    private void persistResultRows(Long executionId, List<Map<String, Object>> rows) {
        if (reportResultRepository == null) {
            throw new IllegalStateException("ReportResultRepository is required for immutable report evidence");
        }
        reportResultRepository.deleteByExecutionId(executionId);
        List<ReportResult> persisted = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            ReportResult result = new ReportResult();
            result.setExecutionId(executionId);
            result.setRowNumber(index + 1);
            result.setData(new LinkedHashMap<>(rows.get(index)));
            persisted.add(result);
        }
        if (!persisted.isEmpty()) {
            reportResultRepository.saveAll(persisted);
        }
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
            bindQueryParameters(query, modifiedSql, parameters, pspId);

            NativeQuery<?> nativeQuery = query.unwrap(NativeQuery.class);
            nativeQuery.setTupleTransformer((tuple, aliases) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < tuple.length; i++) {
                    String alias = aliases != null && i < aliases.length && aliases[i] != null
                            ? aliases[i] : "column_" + (i + 1);
                    row.put(alias, tuple[i]);
                }
                return row;
            });
            return (List<Map<String, Object>>) (List<?>) nativeQuery.getResultList();
            
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
            bindQueryParameters(query, modifiedSql, parameters, pspId);
            
            Number result = (Number) query.getSingleResult();
            return result != null ? result.longValue() : 0L;
            
        } catch (Exception e) {
            logger.error("Count query failed: {}", countQuery, e);
            throw new RuntimeException("Count query failed: " + e.getMessage(), e);
        }
    }

    /**
     * Apply PSP isolation to SQL query
     */
    private String applyPspIsolation(String sql, Long pspId) {
        if (pspId == null || pspIsolationService.isPlatformAdministrator()) {
            return sql;
        }
        if (!hasNamedParameter(sql, "pspId")) {
            throw new SecurityException("Tenant report SQL must declare an explicit :pspId predicate");
        }
        return sql;
    }

    private void bindQueryParameters(Query query, String sql, Map<String, Object> parameters, Long pspId) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        if (parameters != null) bindings.putAll(parameters);
        if (hasNamedParameter(sql, "pspId")) bindings.put("pspId", pspId);

        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            if (hasNamedParameter(sql, entry.getKey())) {
                query.setParameter(entry.getKey(), normalizeParameter(entry.getKey(), entry.getValue()));
            }
        }
    }

    private boolean hasNamedParameter(String sql, String name) {
        return Pattern.compile("(?<!:):" + Pattern.quote(name) + "(?![A-Za-z0-9_])")
                .matcher(sql).find();
    }

    private Object normalizeParameter(String name, Object value) {
        if (!(value instanceof String text) || text.isBlank()
                || !("dateFrom".equals(name) || "dateTo".equals(name))) return value;
        try { return OffsetDateTime.parse(text).toLocalDateTime(); }
        catch (RuntimeException ignored) { }
        try { return LocalDateTime.parse(text); }
        catch (RuntimeException ignored) { }
        LocalDate date = LocalDate.parse(text);
        return "dateTo".equals(name) ? date.plusDays(1).atStartOfDay().minusNanos(1) : date.atStartOfDay();
    }

    private void setExecutionDates(ReportExecution execution, Map<String, Object> parameters) {
        if (parameters == null) return;
        Object from = normalizeParameter("dateFrom", parameters.get("dateFrom"));
        Object to = normalizeParameter("dateTo", parameters.get("dateTo"));
        if (from instanceof LocalDateTime value) execution.setDateFrom(value);
        if (to instanceof LocalDateTime value) execution.setDateTo(value);
    }

    /**
     * Build preview query with limit
     */
    private String buildPreviewQuery(String sql) {
        // Remove any existing limit
        return sql.replaceAll("(?i)LIMIT\\s+\\d+", "").trim();
    }

    /**
     * Export report to file — produces bytes via ReportExportService and writes them
     * to a temp path so that the legacy file-path reference on ReportExecution still works.
     */
    private String exportReport(List<Map<String, Object>> data, Report report, String format, String executionId) {
        String ext = format.equalsIgnoreCase("EXCEL") || format.equalsIgnoreCase("XLSX") ? "xlsx" : format.toLowerCase();
        String fileName = report.getReportCode() + "_" + executionId + "." + ext;
        String filePath = System.getProperty("java.io.tmpdir") + java.io.File.separator + "reports"
                + java.io.File.separator + fileName;

        try {
            java.io.File dir = new java.io.File(filePath).getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }

            byte[] bytes = switch (format.toUpperCase()) {
                case "CSV"                    -> reportExportService.exportToCSV(data);
                case "EXCEL", "XLSX"        -> reportExportService.exportToXLSX(data, report.getReportName());
                case "XML"                  -> reportExportService.exportToXML(data, report.getReportName());
                default                     -> reportExportService.exportToPDF(data, report.getReportName());
            };

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath)) {
                fos.write(bytes);
            }

            logger.info("Report exported to {}: {} bytes", filePath, bytes.length);
            return filePath;

        } catch (Exception e) {
            logger.error("Export failed for format: {}", format, e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    private String canonicalOutputFormat(Object requested) {
        String value = requested == null ? "PDF" : String.valueOf(requested).trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PDF", "CSV", "XML" -> value;
            case "EXCEL", "XLS", "XLSX" -> "XLSX";
            default -> throw new IllegalArgumentException("Unsupported report output format: " + value);
        };
    }

    /**
     * Generate unique execution ID. Public so the controller can mint the ID
     * up-front and hand it to the client for progress polling.
     */
    public String generateExecutionId() {
        return "EXEC_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    String reportFilePath(Long executionId) {
        return reportExecutionRepository.findById(executionId)
                .map(ReportExecution::getFilePath)
                .orElseThrow(() -> new IllegalArgumentException("Report execution not found: " + executionId));
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
        // Physical server paths are never exposed; downloads use the scoped endpoint.
        dto.setFilePath(null);
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
