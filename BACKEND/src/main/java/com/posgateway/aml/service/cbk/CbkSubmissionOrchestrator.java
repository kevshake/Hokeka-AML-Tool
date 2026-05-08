package com.posgateway.aml.service.cbk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.CbkSubmission;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.cbk.CbkGdiClient;
import com.posgateway.aml.integration.cbk.PspCbkContext;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.TransactionRepository;
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
import com.posgateway.aml.service.cbk.mapper.TxnBillingTemplateMapper;
import com.posgateway.aml.service.cbk.mapper.TxnCardBrandMapper;
import com.posgateway.aml.service.cbk.mapper.TxnFailedTransactionMapper;
import com.posgateway.aml.service.cbk.mapper.TxnMerchantTransactionMapper;
import com.posgateway.aml.service.cbk.mapper.TxnSystemActivityMapper;
import com.posgateway.aml.service.cbk.mapper.TxnTransactionDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 *   <li>Pulls source data from the appropriate repository using a date-windowed
 *       query (daily = yesterday, monthly = previous calendar month, annual =
 *       current roster snapshot).</li>
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

    // Transaction-aggregate repository (wired — 6 CBK endpoints)
    private final TransactionRepository transactionRepository;

    // Entity-backed repositories (wired — 11 endpoints source real data)
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
            TransactionRepository transactionRepository,
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
        this.transactionRepository = transactionRepository;
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
     * Dispatches to the correct {@link CbkGdiClient} submit method for {@code type},
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
     * <p>All 17 endpoints now source real data:
     * <ul>
     *   <li>Annual (4): entity-backed current roster — no time window.</li>
     *   <li>Monthly entity (3): windowed to the previous calendar month by createdAt /
     *       dateOfOccurrence / effectiveFrom.</li>
     *   <li>Daily entity (4): windowed to yesterday by incidentDate / reportingDate /
     *       asOfDate.</li>
     *   <li>Transaction-aggregate (6): windowed to yesterday (daily) or the previous
     *       calendar month (monthly) from {@link TransactionRepository}.</li>
     * </ul>
     */
    private Object dispatch(PspCbkContext ctx, CbkEndpointType type) {
        Long pspId = ctx.getPspId();
        String institutionCode = ctx.getInstitutionCode();

        return switch (type) {

            // -----------------------------------------------------------------
            // Annual — current roster snapshot (no date window; always send all)
            // -----------------------------------------------------------------
            case SENIOR_MANAGEMENT -> cbkGdiClient.submitSeniorManagement(ctx,
                    PspSeniorManagementMapper.toRecords(
                            seniorManagementRepository.findByPspId(pspId), institutionCode));

            case DIRECTORS -> cbkGdiClient.submitDirectors(ctx,
                    PspDirectorMapper.toRecords(
                            directorRepository.findByPspId(pspId), institutionCode));

            case TRUSTEES -> cbkGdiClient.submitTrustees(ctx,
                    PspTrusteeMapper.toRecords(
                            trusteeRepository.findByPspId(pspId), institutionCode));

            case SHAREHOLDERS -> cbkGdiClient.submitShareholders(ctx,
                    PspShareholderMapper.toRecords(
                            shareholderRepository.findByPspId(pspId), institutionCode));

            // -----------------------------------------------------------------
            // Monthly — entity-backed, previous calendar month window
            // -----------------------------------------------------------------
            case CUSTOMER_COMPLAINTS -> {
                LocalDate prevMonthStart = startOfPreviousMonth();
                LocalDate prevMonthEnd   = endOfPreviousMonth();
                yield cbkGdiClient.submitCustomerComplaints(ctx,
                        PspCustomerComplaintMapper.toRecords(
                                customerComplaintRepository.findByPspIdAndDateOfOccurrenceBetween(
                                        pspId, prevMonthStart, prevMonthEnd),
                                institutionCode));
            }

            case PRODUCTS_INFO -> {
                LocalDateTime prevMonthStartDt = startOfPreviousMonthDateTime();
                LocalDateTime prevMonthEndDt   = endOfPreviousMonthDateTime();
                yield cbkGdiClient.submitProducts(ctx,
                        PspProductMapper.toRecords(
                                productRepository.findByPspIdAndCreatedAtBetween(
                                        pspId, prevMonthStartDt, prevMonthEndDt),
                                institutionCode));
            }

            case TRANSACTION_TARIFFS -> {
                LocalDate prevMonthStart = startOfPreviousMonth();
                LocalDate prevMonthEnd   = endOfPreviousMonth();
                yield cbkGdiClient.submitTransactionTariffs(ctx,
                        PspTariffTemplateMapper.toRecords(
                                tariffTemplateRepository.findByPspIdAndEffectiveFromBetween(
                                        pspId, prevMonthStart, prevMonthEnd),
                                institutionCode));
            }

            // -----------------------------------------------------------------
            // Daily — entity-backed, yesterday window
            // -----------------------------------------------------------------
            case CYBER_INCIDENT -> {
                LocalDateTime yesterdayStart = startOfYesterdayDateTime();
                LocalDateTime yesterdayEnd   = startOfTodayDateTime();
                yield cbkGdiClient.submitCyberIncidents(ctx,
                        PspCyberIncidentMapper.toRecords(
                                cyberIncidentRepository.findByPspIdAndIncidentDateBetween(
                                        pspId, yesterdayStart, yesterdayEnd),
                                institutionCode));
            }

            case FRAUD_INCIDENTS -> {
                LocalDate yesterday = startOfYesterday();
                LocalDate today     = LocalDate.now(ZoneOffset.UTC);
                yield cbkGdiClient.submitFraudIncidents(ctx,
                        PspFraudIncidentMapper.toRecords(
                                fraudIncidentRepository.findByPspIdAndReportingDateBetween(
                                        pspId, yesterday, today),
                                institutionCode));
            }

            case SYSTEM_STABILITY -> {
                LocalDate yesterday = startOfYesterday();
                LocalDate today     = LocalDate.now(ZoneOffset.UTC);
                yield cbkGdiClient.submitSystemStability(ctx,
                        PspSystemInterruptionMapper.toRecords(
                                systemInterruptionRepository.findByPspIdAndReportingDateBetween(
                                        pspId, yesterday, today),
                                institutionCode));
            }

            case TRUST_ACCOUNT -> {
                LocalDate yesterday = startOfYesterday();
                LocalDate today     = LocalDate.now(ZoneOffset.UTC);
                yield cbkGdiClient.submitTrustAccounts(ctx,
                        PspTrustAccountMapper.toRecords(
                                trustAccountRepository.findByPspIdAndAsOfDateBetween(
                                        pspId, yesterday, today),
                                institutionCode));
            }

            // -----------------------------------------------------------------
            // Monthly — transaction-aggregate, previous calendar month window
            // -----------------------------------------------------------------
            case CARD_BRANDS -> {
                LocalDateTime start = startOfPreviousMonthDateTime();
                LocalDateTime end   = endOfPreviousMonthDateTime();
                yield cbkGdiClient.submitCardBrands(ctx,
                        TxnCardBrandMapper.toRecords(
                                transactionRepository.aggregateCardBrandsByPspAndWindow(pspId, start, end),
                                institutionCode));
            }

            case TRANSACTION_DETAILS -> {
                LocalDateTime start = startOfPreviousMonthDateTime();
                LocalDateTime end   = endOfPreviousMonthDateTime();
                yield cbkGdiClient.submitTransactionDetails(ctx,
                        TxnTransactionDetailMapper.toRecords(
                                transactionRepository.aggregateTransactionDetailsByPspAndWindow(pspId, start, end),
                                institutionCode));
            }

            // -----------------------------------------------------------------
            // Daily — transaction-aggregate, yesterday window
            // -----------------------------------------------------------------
            case SYSTEM_ACTIVITY -> {
                LocalDateTime start = startOfYesterdayDateTime();
                LocalDateTime end   = startOfTodayDateTime();
                yield cbkGdiClient.submitSystemActivity(ctx,
                        TxnSystemActivityMapper.toRecords(
                                transactionRepository.aggregateHourlyActivityByPspAndWindow(pspId, start, end),
                                institutionCode));
            }

            case BILLING_TEMPLATE -> {
                LocalDateTime start = startOfYesterdayDateTime();
                LocalDateTime end   = startOfTodayDateTime();
                yield cbkGdiClient.submitBillingTemplate(ctx,
                        TxnBillingTemplateMapper.toRecords(
                                transactionRepository.aggregateBillingClassificationByPspAndWindow(pspId, start, end),
                                institutionCode));
            }

            case MERCHANT_TRANSACTIONS -> {
                LocalDateTime start = startOfYesterdayDateTime();
                LocalDateTime end   = startOfTodayDateTime();
                yield cbkGdiClient.submitMerchantTransactions(ctx,
                        TxnMerchantTransactionMapper.toRecords(
                                transactionRepository.aggregateMerchantSettlementByPspAndWindow(pspId, start, end),
                                institutionCode));
            }

            case FAILED_TRANSACTIONS -> {
                LocalDateTime start = startOfYesterdayDateTime();
                LocalDateTime end   = startOfTodayDateTime();
                yield cbkGdiClient.submitFailedTransactions(ctx,
                        TxnFailedTransactionMapper.toRecords(
                                transactionRepository.aggregateFailedTransactionsByPspAndWindow(pspId, start, end),
                                institutionCode));
            }
        };
    }

    // =========================================================================
    // Date-window helpers (UTC)
    // =========================================================================

    /** Midnight UTC at the start of yesterday (inclusive lower bound for daily windows). */
    private static LocalDate startOfYesterday() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(1);
    }

    /** Midnight UTC at the start of yesterday as LocalDateTime. */
    private static LocalDateTime startOfYesterdayDateTime() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay();
    }

    /** Midnight UTC at the start of today (exclusive upper bound for daily windows). */
    private static LocalDateTime startOfTodayDateTime() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay();
    }

    /** First day of the previous calendar month (inclusive lower bound). */
    private static LocalDate startOfPreviousMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1);
    }

    /** First day of the previous calendar month as LocalDateTime. */
    private static LocalDateTime startOfPreviousMonthDateTime() {
        return startOfPreviousMonth().atStartOfDay();
    }

    /** First day of the current calendar month (exclusive upper bound for monthly windows). */
    private static LocalDate endOfPreviousMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    }

    /** First day of the current calendar month as LocalDateTime (exclusive upper bound). */
    private static LocalDateTime endOfPreviousMonthDateTime() {
        return endOfPreviousMonth().atStartOfDay();
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
