package com.posgateway.aml.service.cbk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.CbkSubmission;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.cbk.CbkGdiClient;
import com.posgateway.aml.integration.cbk.PspCbkContext;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.compliance.CbkSubmissionRepository;
import com.posgateway.aml.repository.psp.cbk.PspCyberIncidentRepository;
import com.posgateway.aml.repository.psp.cbk.PspCustomerComplaintRepository;
import com.posgateway.aml.repository.psp.cbk.PspDirectorRepository;
import com.posgateway.aml.repository.psp.cbk.PspFraudIncidentRepository;
import com.posgateway.aml.repository.psp.cbk.PspProductRepository;
import com.posgateway.aml.repository.psp.cbk.PspSeniorManagementRepository;
import com.posgateway.aml.repository.psp.cbk.PspShareholderRepository;
import com.posgateway.aml.repository.psp.cbk.PspSystemInterruptionRepository;
import com.posgateway.aml.repository.psp.cbk.PspTariffTemplateRepository;
import com.posgateway.aml.repository.psp.cbk.PspTrustAccountRepository;
import com.posgateway.aml.repository.psp.cbk.PspTrusteeRepository;
import com.posgateway.aml.service.cbk.mapper.PspCyberIncidentMapper;
import com.posgateway.aml.service.cbk.mapper.PspCustomerComplaintMapper;
import com.posgateway.aml.service.cbk.mapper.PspDirectorMapper;
import com.posgateway.aml.service.cbk.mapper.PspFraudIncidentMapper;
import com.posgateway.aml.service.cbk.mapper.PspProductMapper;
import com.posgateway.aml.service.cbk.mapper.PspSeniorManagementMapper;
import com.posgateway.aml.service.cbk.mapper.PspShareholderMapper;
import com.posgateway.aml.service.cbk.mapper.PspSystemInterruptionMapper;
import com.posgateway.aml.service.cbk.mapper.PspTariffTemplateMapper;
import com.posgateway.aml.service.cbk.mapper.PspTrustAccountMapper;
import com.posgateway.aml.service.cbk.mapper.PspTrusteeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates CBK GDI scheduled submissions across all eligible PSPs.
 *
 * <p>For each invocation the orchestrator:
 * <ol>
 *   <li>Collects all PSPs where {@code cbkReportingEnabled=true} and
 *       {@code cbk_institution_code} is non-null.</li>
 *   <li>For each PSP resolves credentials via {@link PspCbkConfigResolver}.</li>
 *   <li>Pulls source data from the appropriate repository (or passes an empty
 *       list at wiring points not yet connected).</li>
 *   <li>Calls the matching {@link CbkGdiClient} method.</li>
 *   <li>Persists a {@link CbkSubmission} row regardless of success or failure.</li>
 * </ol>
 *
 * <p>This class holds no cron logic — scheduling is done by
 * {@link com.posgateway.aml.scheduler.cbk.CbkScheduler}.
 */
@Service
public class CbkSubmissionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CbkSubmissionOrchestrator.class);

    private final PspRepository pspRepository;
    private final CbkSubmissionRepository submissionRepository;
    private final PspCbkConfigResolver configResolver;
    private final CbkGdiClient cbkGdiClient;
    private final ObjectMapper objectMapper;

    // Entity-backed repositories (wired — 11 endpoints now source real data)
    private final PspSeniorManagementRepository seniorManagementRepository;
    private final PspDirectorRepository directorRepository;
    private final PspTrusteeRepository trusteeRepository;
    private final PspShareholderRepository shareholderRepository;
    private final PspCustomerComplaintRepository customerComplaintRepository;
    private final PspProductRepository productRepository;
    private final PspTariffTemplateRepository tariffTemplateRepository;
    private final PspCyberIncidentRepository cyberIncidentRepository;
    private final PspFraudIncidentRepository fraudIncidentRepository;
    private final PspSystemInterruptionRepository systemInterruptionRepository;
    private final PspTrustAccountRepository trustAccountRepository;

    public CbkSubmissionOrchestrator(
            PspRepository pspRepository,
            CbkSubmissionRepository submissionRepository,
            PspCbkConfigResolver configResolver,
            CbkGdiClient cbkGdiClient,
            ObjectMapper objectMapper,
            PspSeniorManagementRepository seniorManagementRepository,
            PspDirectorRepository directorRepository,
            PspTrusteeRepository trusteeRepository,
            PspShareholderRepository shareholderRepository,
            PspCustomerComplaintRepository customerComplaintRepository,
            PspProductRepository productRepository,
            PspTariffTemplateRepository tariffTemplateRepository,
            PspCyberIncidentRepository cyberIncidentRepository,
            PspFraudIncidentRepository fraudIncidentRepository,
            PspSystemInterruptionRepository systemInterruptionRepository,
            PspTrustAccountRepository trustAccountRepository) {
        this.pspRepository = pspRepository;
        this.submissionRepository = submissionRepository;
        this.configResolver = configResolver;
        this.cbkGdiClient = cbkGdiClient;
        this.objectMapper = objectMapper;
        this.seniorManagementRepository = seniorManagementRepository;
        this.directorRepository = directorRepository;
        this.trusteeRepository = trusteeRepository;
        this.shareholderRepository = shareholderRepository;
        this.customerComplaintRepository = customerComplaintRepository;
        this.productRepository = productRepository;
        this.tariffTemplateRepository = tariffTemplateRepository;
        this.cyberIncidentRepository = cyberIncidentRepository;
        this.fraudIncidentRepository = fraudIncidentRepository;
        this.systemInterruptionRepository = systemInterruptionRepository;
        this.trustAccountRepository = trustAccountRepository;
    }

    // =========================================================================
    // Public orchestration methods
    // =========================================================================

    /**
     * Fires all 8 daily CBK endpoints for every eligible PSP.
     * Called by {@link com.posgateway.aml.scheduler.cbk.CbkScheduler} at 02:00.
     */
    public void runDailyCbkSubmissionsForAllPsps() {
        List<Psp> psps = findEligiblePsps();
        log.info("CBK daily run: {} eligible PSP(s)", psps.size());
        for (Psp psp : psps) {
            submitDailyForPsp(psp.getPspId());
        }
    }

    /**
     * Fires the monthly CBK endpoints whose {@link CbkEndpointType#getTargetDay()}
     * matches {@code cbkScheduleDay}.
     *
     * <p>Cron passes {@code 1}, {@code 2}, or {@code 3} depending on the day-of-month.
     */
    public void runMonthlyCbkSubmissionsForAllPsps(int cbkScheduleDay) {
        List<Psp> psps = findEligiblePsps();
        log.info("CBK monthly run (day={}): {} eligible PSP(s)", cbkScheduleDay, psps.size());
        for (Psp psp : psps) {
            submitMonthlyForPsp(psp.getPspId(), cbkScheduleDay);
        }
    }

    /**
     * Fires the annual CBK endpoints whose {@link CbkEndpointType#getTargetDay()}
     * matches {@code monthDay}.
     *
     * <p>Cron passes {@code 4} (Jan 4) or {@code 5} (Jan 5).
     */
    public void runAnnualCbkSubmissionsForAllPsps(int monthDay) {
        List<Psp> psps = findEligiblePsps();
        log.info("CBK annual run (day={}): {} eligible PSP(s)", monthDay, psps.size());
        for (Psp psp : psps) {
            submitAnnualForPsp(psp.getPspId(), monthDay);
        }
    }

    /**
     * Manual retrigger for a single PSP + endpoint. Used by the admin retry
     * controller endpoint.
     *
     * @param pspId        target PSP
     * @param endpointType which of the 17 endpoints to fire
     * @return result object with outcome, HTTP status, and persisted submission id
     */
    public CbkSubmissionResult runSingleEndpoint(Long pspId, CbkEndpointType endpointType) {
        Optional<PspCbkContext> ctxOpt = configResolver.resolve(pspId);
        if (ctxOpt.isEmpty()) {
            log.warn("CBK manual run skipped: PSP {} not eligible for endpoint {}", pspId, endpointType);
            return CbkSubmissionResult.builder()
                    .pspId(pspId)
                    .endpointType(endpointType)
                    .outcome(CbkSubmissionResult.Outcome.SKIPPED)
                    .httpStatus(0)
                    .errorMessage("PSP not eligible: cbkReportingEnabled=false or missing institution code")
                    .build();
        }
        return executeEndpoint(ctxOpt.get(), endpointType);
    }

    // =========================================================================
    // Private per-PSP helpers
    // =========================================================================

    private void submitDailyForPsp(Long pspId) {
        Optional<PspCbkContext> ctxOpt = configResolver.resolve(pspId);
        if (ctxOpt.isEmpty()) return;
        PspCbkContext ctx = ctxOpt.get();
        List<CbkEndpointType> daily = List.of(
                CbkEndpointType.CYBER_INCIDENT,
                CbkEndpointType.FRAUD_INCIDENTS,
                CbkEndpointType.SYSTEM_STABILITY,
                CbkEndpointType.SYSTEM_ACTIVITY,
                CbkEndpointType.TRUST_ACCOUNT,
                CbkEndpointType.BILLING_TEMPLATE,
                CbkEndpointType.MERCHANT_TRANSACTIONS,
                CbkEndpointType.FAILED_TRANSACTIONS
        );
        for (CbkEndpointType type : daily) {
            safeExecute(ctx, type);
        }
    }

    private void submitMonthlyForPsp(Long pspId, int cbkScheduleDay) {
        Optional<PspCbkContext> ctxOpt = configResolver.resolve(pspId);
        if (ctxOpt.isEmpty()) return;
        PspCbkContext ctx = ctxOpt.get();
        List<CbkEndpointType> monthly = List.of(
                CbkEndpointType.CUSTOMER_COMPLAINTS,
                CbkEndpointType.PRODUCTS_INFO,
                CbkEndpointType.CARD_BRANDS,
                CbkEndpointType.TRANSACTION_DETAILS,
                CbkEndpointType.TRANSACTION_TARIFFS
        );
        for (CbkEndpointType type : monthly) {
            if (type.getTargetDay() == cbkScheduleDay) {
                safeExecute(ctx, type);
            }
        }
    }

    private void submitAnnualForPsp(Long pspId, int monthDay) {
        Optional<PspCbkContext> ctxOpt = configResolver.resolve(pspId);
        if (ctxOpt.isEmpty()) return;
        PspCbkContext ctx = ctxOpt.get();
        List<CbkEndpointType> annual = List.of(
                CbkEndpointType.SENIOR_MANAGEMENT,
                CbkEndpointType.DIRECTORS,
                CbkEndpointType.TRUSTEES,
                CbkEndpointType.SHAREHOLDERS
        );
        for (CbkEndpointType type : annual) {
            if (type.getTargetDay() == monthDay) {
                safeExecute(ctx, type);
            }
        }
    }

    /** Wraps {@link #executeEndpoint} to catch any unchecked exception so one PSP
     *  failure does not abort the rest of the batch. */
    private void safeExecute(PspCbkContext ctx, CbkEndpointType type) {
        try {
            CbkSubmissionResult result = executeEndpoint(ctx, type);
            if (!result.isSuccess()) {
                log.warn("CBK submission non-success: pspId={} endpoint={} outcome={} http={}",
                        ctx.getPspId(), type, result.getOutcome(), result.getHttpStatus());
            }
        } catch (Exception ex) {
            log.error("CBK submission unexpected error: pspId={} endpoint={}", ctx.getPspId(), type, ex);
        }
    }

    // =========================================================================
    // Core dispatch: build record list, call client, persist audit row
    // =========================================================================

    /**
     * Dispatches to the correct {@link CbkGdiClient} method for {@code type},
     * then persists an audit {@link CbkSubmission} row regardless of outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CbkSubmissionResult executeEndpoint(PspCbkContext ctx, CbkEndpointType type) {
        Long pspId = ctx.getPspId();
        log.debug("CBK execute: pspId={} endpoint={}", pspId, type);

        String requestExcerpt;
        Object responseObj;
        CbkSubmission.Status status;
        String errorMessage = null;
        int httpStatus = 0;

        try {
            // Dispatch
            responseObj = dispatch(ctx, type);
            status = CbkSubmission.Status.SUBMITTED;
            requestExcerpt = buildRequestExcerpt(ctx, type);
        } catch (Exception ex) {
            log.error("CBK GDI client error: pspId={} endpoint={} error={}", pspId, type, ex.getMessage(), ex);
            requestExcerpt = buildRequestExcerpt(ctx, type);
            responseObj = null;
            status = CbkSubmission.Status.REJECTED;
            errorMessage = truncate(ex.getMessage(), 1024);
        }

        // Audit row
        CbkSubmission row = buildAuditRow(pspId, type, requestExcerpt, responseObj, status, errorMessage);
        CbkSubmission saved = submissionRepository.save(row);

        CbkSubmissionResult.Outcome outcome = (status == CbkSubmission.Status.SUBMITTED)
                ? CbkSubmissionResult.Outcome.SUCCESS
                : CbkSubmissionResult.Outcome.FAILURE;

        return CbkSubmissionResult.builder()
                .pspId(pspId)
                .endpointType(type)
                .outcome(outcome)
                .httpStatus(httpStatus)
                .referenceNumber(saved.getReferenceNumber())
                .errorMessage(errorMessage)
                .submissionId(saved.getId())
                .build();
    }

    /**
     * Routes to the correct {@link CbkGdiClient} submit method.
     *
     * <p>11 entity-backed endpoints now fetch real data via their repositories and
     * mappers. 6 transaction-aggregate endpoints remain stubbed pending aggregation
     * query implementation.
     */
    private Object dispatch(PspCbkContext ctx, CbkEndpointType type) {
        Long pspId = ctx.getPspId();
        String institutionCode = ctx.getInstitutionCode();
        return switch (type) {

            // --- Annual (entity-backed) ---
            case SENIOR_MANAGEMENT -> {
                // TODO add date-windowed query to PspSeniorManagementRepository
                yield cbkGdiClient.submitSeniorManagement(ctx,
                        PspSeniorManagementMapper.toRecords(
                                seniorManagementRepository.findByPspId(pspId), institutionCode));
            }
            case DIRECTORS -> {
                // TODO add date-windowed query to PspDirectorRepository
                yield cbkGdiClient.submitDirectors(ctx,
                        PspDirectorMapper.toRecords(
                                directorRepository.findByPspId(pspId), institutionCode));
            }
            case TRUSTEES -> {
                // TODO add date-windowed query to PspTrusteeRepository
                yield cbkGdiClient.submitTrustees(ctx,
                        PspTrusteeMapper.toRecords(
                                trusteeRepository.findByPspId(pspId), institutionCode));
            }
            case SHAREHOLDERS -> {
                // TODO add date-windowed query to PspShareholderRepository
                yield cbkGdiClient.submitShareholders(ctx,
                        PspShareholderMapper.toRecords(
                                shareholderRepository.findByPspId(pspId), institutionCode));
            }

            // --- Monthly (entity-backed) ---
            case CUSTOMER_COMPLAINTS -> {
                // TODO add date-windowed query to PspCustomerComplaintRepository
                yield cbkGdiClient.submitCustomerComplaints(ctx,
                        PspCustomerComplaintMapper.toRecords(
                                customerComplaintRepository.findByPspId(pspId), institutionCode));
            }
            case PRODUCTS_INFO -> {
                // TODO add date-windowed query to PspProductRepository
                yield cbkGdiClient.submitProducts(ctx,
                        PspProductMapper.toRecords(
                                productRepository.findByPspId(pspId), institutionCode));
            }
            case TRANSACTION_TARIFFS -> {
                // TODO add date-windowed query to PspTariffTemplateRepository
                yield cbkGdiClient.submitTransactionTariffs(ctx,
                        PspTariffTemplateMapper.toRecords(
                                tariffTemplateRepository.findByPspId(pspId), institutionCode));
            }

            // --- Daily (entity-backed) ---
            case CYBER_INCIDENT -> {
                // TODO add date-windowed query to PspCyberIncidentRepository
                yield cbkGdiClient.submitCyberIncidents(ctx,
                        PspCyberIncidentMapper.toRecords(
                                cyberIncidentRepository.findByPspId(pspId), institutionCode));
            }
            case FRAUD_INCIDENTS -> {
                // TODO add date-windowed query to PspFraudIncidentRepository
                yield cbkGdiClient.submitFraudIncidents(ctx,
                        PspFraudIncidentMapper.toRecords(
                                fraudIncidentRepository.findByPspId(pspId), institutionCode));
            }
            case SYSTEM_STABILITY -> {
                // TODO add date-windowed query to PspSystemInterruptionRepository
                yield cbkGdiClient.submitSystemStability(ctx,
                        PspSystemInterruptionMapper.toRecords(
                                systemInterruptionRepository.findByPspId(pspId), institutionCode));
            }
            case TRUST_ACCOUNT -> {
                // TODO add date-windowed query to PspTrustAccountRepository
                yield cbkGdiClient.submitTrustAccounts(ctx,
                        PspTrustAccountMapper.toRecords(
                                trustAccountRepository.findByPspId(pspId), institutionCode));
            }

            // --- Transaction-aggregate stubs (needs aggregation queries — handled separately) ---
            case CARD_BRANDS -> {
                // TODO source from TransactionRepository — aggregate by card_brand_type for previous month
                yield cbkGdiClient.submitCardBrands(ctx, Collections.emptyList());
            }
            case TRANSACTION_DETAILS -> {
                // TODO source from TransactionRepository — aggregate by brand/type/class/channel for previous month
                yield cbkGdiClient.submitTransactionDetails(ctx, Collections.emptyList());
            }
            case SYSTEM_ACTIVITY -> {
                // TODO source from TransactionRepository — aggregate TPS+TPH per hour for yesterday, PSP-scoped
                yield cbkGdiClient.submitSystemActivity(ctx, Collections.emptyList());
            }
            case BILLING_TEMPLATE -> {
                // TODO source from TransactionRepository — aggregate by bill_classification_code for yesterday
                yield cbkGdiClient.submitBillingTemplate(ctx, Collections.emptyList());
            }
            case MERCHANT_TRANSACTIONS -> {
                // TODO source from TransactionRepository.findSuccessfulYesterdayByPspId(pspId)
                yield cbkGdiClient.submitMerchantTransactions(ctx, Collections.emptyList());
            }
            case FAILED_TRANSACTIONS -> {
                // TODO source from TransactionRepository — filter status=FAILED|REJECTED for yesterday
                yield cbkGdiClient.submitFailedTransactions(ctx, Collections.emptyList());
            }
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<Psp> findEligiblePsps() {
        return pspRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getCbkReportingEnabled()))
                .filter(p -> p.getCbkInstitutionCode() != null && !p.getCbkInstitutionCode().isBlank())
                .toList();
    }

    private CbkSubmission buildAuditRow(Long pspId, CbkEndpointType type,
                                         String requestExcerpt, Object responseObj,
                                         CbkSubmission.Status status, String errorMessage) {
        Instant now = Instant.now();
        String ref = "CBK-" + now.atZone(ZoneOffset.UTC).getYear()
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        String period = switch (type.getCadence()) {
            case DAILY -> LocalDate.now(ZoneOffset.UTC).minusDays(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            case MONTHLY -> LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case ANNUAL -> String.valueOf(LocalDate.now(ZoneOffset.UTC).minusYears(1).getYear());
        };

        CbkSubmission row = new CbkSubmission();
        row.setPspId(pspId);
        row.setReportType(type.name());
        row.setPeriod(period);
        row.setReferenceNumber(ref);
        row.setStatus(status);
        row.setSubmittedAt(now);
        row.setPayloadJson(requestExcerpt);
        row.setRegulatorResponse(serializeSafe(responseObj));
        row.setErrorMessage(errorMessage != null ? truncate(errorMessage, 1024) : null);
        return row;
    }

    private String buildRequestExcerpt(PspCbkContext ctx, CbkEndpointType type) {
        // Minimal excerpt for audit traceability — does not include credentials.
        return "{\"pspId\":" + ctx.getPspId()
                + ",\"institutionCode\":\"" + ctx.getInstitutionCode() + "\""
                + ",\"endpoint\":\"" + type.name() + "\""
                + ",\"wrapperKey\":\"" + type.getWrapperKey() + "\"}";
    }

    private String serializeSafe(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
