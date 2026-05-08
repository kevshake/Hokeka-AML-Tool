package com.posgateway.aml.service.cbk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.CbkSubmission;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.cbk.CbkGdiClient;
import com.posgateway.aml.integration.cbk.PspCbkContext;
import com.posgateway.aml.integration.cbk.records.BillingTemplateRecord;
import com.posgateway.aml.integration.cbk.records.CardBrandRecord;
import com.posgateway.aml.integration.cbk.records.FailedTransactionRecord;
import com.posgateway.aml.integration.cbk.records.MerchantTransactionRecord;
import com.posgateway.aml.integration.cbk.records.SystemActivityRecord;
import com.posgateway.aml.integration.cbk.records.TransactionDetailRecord;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
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

    // Transaction-aggregate repository (used for the 6 GDI transaction endpoints)
    private final TransactionRepository transactionRepository;

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
            PspTrustAccountRepository trustAccountRepository,
            TransactionRepository transactionRepository) {
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
        this.transactionRepository = transactionRepository;
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
     * <p>All 17 endpoints now source real data: 11 from entity repositories with
     * date-windowed queries, 6 from {@link TransactionRepository} aggregations.
     * The reporting window is derived from the endpoint's {@link CbkEndpointType.Cadence}.
     */
    private Object dispatch(PspCbkContext ctx, CbkEndpointType type) {
        Long pspId = ctx.getPspId();
        String institutionCode = ctx.getInstitutionCode();
        ReportingWindow window = computeWindow(type);
        LocalDate windowStart = window.start();
        LocalDate windowEnd = window.end();
        LocalDateTime windowStartTs = windowStart.atStartOfDay();
        LocalDateTime windowEndTs = windowEnd.atTime(23, 59, 59);
        String reportingDate = windowEnd.format(DateTimeFormatter.ISO_LOCAL_DATE);

        return switch (type) {

            // --- Annual (entity-backed) ---
            case SENIOR_MANAGEMENT -> cbkGdiClient.submitSeniorManagement(ctx,
                    PspSeniorManagementMapper.toRecords(
                            seniorManagementRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case DIRECTORS -> cbkGdiClient.submitDirectors(ctx,
                    PspDirectorMapper.toRecords(
                            directorRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case TRUSTEES -> cbkGdiClient.submitTrustees(ctx,
                    PspTrusteeMapper.toRecords(
                            trusteeRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case SHAREHOLDERS -> cbkGdiClient.submitShareholders(ctx,
                    PspShareholderMapper.toRecords(
                            shareholderRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));

            // --- Monthly (entity-backed) ---
            case CUSTOMER_COMPLAINTS -> cbkGdiClient.submitCustomerComplaints(ctx,
                    PspCustomerComplaintMapper.toRecords(
                            customerComplaintRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case PRODUCTS_INFO -> cbkGdiClient.submitProducts(ctx,
                    PspProductMapper.toRecords(
                            productRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case TRANSACTION_TARIFFS -> cbkGdiClient.submitTransactionTariffs(ctx,
                    PspTariffTemplateMapper.toRecords(
                            tariffTemplateRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));

            // --- Daily (entity-backed) ---
            case CYBER_INCIDENT -> cbkGdiClient.submitCyberIncidents(ctx,
                    PspCyberIncidentMapper.toRecords(
                            cyberIncidentRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case FRAUD_INCIDENTS -> cbkGdiClient.submitFraudIncidents(ctx,
                    PspFraudIncidentMapper.toRecords(
                            fraudIncidentRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case SYSTEM_STABILITY -> cbkGdiClient.submitSystemStability(ctx,
                    PspSystemInterruptionMapper.toRecords(
                            systemInterruptionRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));
            case TRUST_ACCOUNT -> cbkGdiClient.submitTrustAccounts(ctx,
                    PspTrustAccountMapper.toRecords(
                            trustAccountRepository.findActiveInWindow(pspId, windowStart, windowEnd),
                            institutionCode));

            // --- Transaction-aggregate (sourced from TransactionRepository) ---
            case CARD_BRANDS -> cbkGdiClient.submitCardBrands(ctx,
                    buildCardBrandRecords(pspId, institutionCode, reportingDate, windowStartTs, windowEndTs));
            case TRANSACTION_DETAILS -> cbkGdiClient.submitTransactionDetails(ctx,
                    buildTransactionDetailRecords(pspId, reportingDate, windowStartTs, windowEndTs));
            case SYSTEM_ACTIVITY -> cbkGdiClient.submitSystemActivity(ctx,
                    buildSystemActivityRecords(pspId, institutionCode, reportingDate, windowStartTs, windowEndTs));
            case BILLING_TEMPLATE -> cbkGdiClient.submitBillingTemplate(ctx,
                    buildBillingTemplateRecords(pspId, reportingDate, windowStartTs, windowEndTs));
            case MERCHANT_TRANSACTIONS -> cbkGdiClient.submitMerchantTransactions(ctx,
                    buildMerchantTransactionRecords(pspId, institutionCode, reportingDate, windowStartTs, windowEndTs));
            case FAILED_TRANSACTIONS -> cbkGdiClient.submitFailedTransactions(ctx,
                    buildFailedTransactionRecords(pspId, institutionCode, reportingDate, windowStartTs, windowEndTs));
        };
    }

    // =========================================================================
    // Reporting-window computation
    // =========================================================================

    /** Inclusive [start, end] reporting window for a CBK endpoint. */
    private record ReportingWindow(LocalDate start, LocalDate end) {}

    /**
     * Derives the reporting window from the cadence:
     * <ul>
     *   <li>DAILY  -&gt; yesterday (1 day)</li>
     *   <li>MONTHLY -&gt; first..last day of previous calendar month</li>
     *   <li>ANNUAL -&gt; Jan 1 .. Dec 31 of previous year</li>
     * </ul>
     */
    private static ReportingWindow computeWindow(CbkEndpointType type) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (type.getCadence()) {
            case DAILY -> {
                LocalDate yesterday = today.minusDays(1);
                yield new ReportingWindow(yesterday, yesterday);
            }
            case MONTHLY -> {
                LocalDate prevMonthFirst = today.minusMonths(1).withDayOfMonth(1);
                LocalDate prevMonthLast = prevMonthFirst.with(TemporalAdjusters.lastDayOfMonth());
                yield new ReportingWindow(prevMonthFirst, prevMonthLast);
            }
            case ANNUAL -> {
                int prevYear = today.getYear() - 1;
                yield new ReportingWindow(LocalDate.of(prevYear, 1, 1), LocalDate.of(prevYear, 12, 31));
            }
        };
    }

    // =========================================================================
    // Transaction-aggregate builders (CBK GDI #9, #12, #13, #14, #16, #17)
    //
    // TransactionEntity has no card_brand, bill_classification_code, channel, or
    // card_class_type columns. Substitutes are documented per builder.
    // =========================================================================

    private static String formatAmountFromCents(Object amountCents) {
        if (amountCents == null) return "0.00";
        long cents = ((Number) amountCents).longValue();
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** Endpoint #12 — substitute: {@code direction} stands in for card brand type. */
    private List<CardBrandRecord> buildCardBrandRecords(Long pspId, String institutionCode,
                                                        String reportingDate,
                                                        LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = transactionRepository.findCardBrandSummaryForPsp(pspId, start, end);
        List<CardBrandRecord> out = new ArrayList<>(rows.size());
        int rowCounter = 1;
        for (Object[] r : rows) {
            CardBrandRecord rec = new CardBrandRecord();
            rec.setRowId(institutionCode + "-CB-" + reportingDate + "-" + rowCounter++);
            rec.setReportingDate(reportingDate);
            rec.setBankId(institutionCode);
            rec.setTransactionCategory(String.valueOf(r[0]));
            rec.setCardBrandType(String.valueOf(r[0]));
            rec.setNumberOfTxns(String.valueOf(((Number) r[1]).longValue()));
            rec.setValueOfTxns(formatAmountFromCents(r[2]));
            out.add(rec);
        }
        return out;
    }

    /** Endpoint #14 — substitute: direction × decision × merchant_country stand in for brand × type × channel. */
    private List<TransactionDetailRecord> buildTransactionDetailRecords(Long pspId,
                                                                         String reportingDate,
                                                                         LocalDateTime start,
                                                                         LocalDateTime end) {
        List<Object[]> rows = transactionRepository.findTransactionMixForPsp(pspId, start, end);
        List<TransactionDetailRecord> out = new ArrayList<>(rows.size());
        int rowCounter = 1;
        for (Object[] r : rows) {
            TransactionDetailRecord rec = new TransactionDetailRecord();
            rec.setReportingDate(reportingDate);
            rec.setRowId("TD-" + reportingDate + "-" + rowCounter++);
            rec.setCardBrandType(String.valueOf(r[0]));
            rec.setCardType(String.valueOf(r[0]));
            rec.setCardClassType(String.valueOf(r[1]));
            rec.setTransactionCategoryType(String.valueOf(r[1]));
            rec.setChannelType(String.valueOf(r[2]));
            rec.setTotalNumberOfTransactionsDone(String.valueOf(((Number) r[3]).longValue()));
            rec.setTotalValueOfTransactionsDone(formatAmountFromCents(r[4]));
            out.add(rec);
        }
        return out;
    }

    /** Endpoint #9 — 24 rows per day (TPS+TPH per hour). Hours with zero traffic emit a zero row. */
    private List<SystemActivityRecord> buildSystemActivityRecords(Long pspId, String institutionCode,
                                                                   String reportingDate,
                                                                   LocalDateTime start,
                                                                   LocalDateTime end) {
        List<Object[]> rows = transactionRepository.findHourlyTpsTphForPsp(pspId, start, end);
        long[] perHour = new long[24];
        for (Object[] r : rows) {
            int hour = ((Number) r[0]).intValue();
            long count = ((Number) r[1]).longValue();
            if (hour >= 0 && hour < 24) perHour[hour] = count;
        }
        List<SystemActivityRecord> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            SystemActivityRecord rec = new SystemActivityRecord();
            rec.setPspId(institutionCode);
            rec.setReportingDate(reportingDate);
            rec.setHourOfTheDay(String.format("%02d", h));
            // TPS = ceil(count / 3600.0). For low-volume hours this yields 0.
            long tps = perHour[h] == 0 ? 0L : (perHour[h] + 3599L) / 3600L;
            rec.setNumberOfTxnsPerSec(String.valueOf(tps));
            rec.setNumberOfTransactionsPerHour(String.valueOf(perHour[h]));
            out.add(rec);
        }
        return out;
    }

    /** Endpoint #13 — substitute: {@code merchant_country} stands in for bill_classification_code. */
    private List<BillingTemplateRecord> buildBillingTemplateRecords(Long pspId, String reportingDate,
                                                                     LocalDateTime start,
                                                                     LocalDateTime end) {
        List<Object[]> rows = transactionRepository.findBillClassificationSummaryForPsp(pspId, start, end);
        List<BillingTemplateRecord> out = new ArrayList<>(rows.size());
        int rowCounter = 1;
        for (Object[] r : rows) {
            BillingTemplateRecord rec = new BillingTemplateRecord();
            rec.setRowId("BT-" + reportingDate + "-" + rowCounter++);
            rec.setReportingDate(reportingDate);
            rec.setBillClassificationCode(String.valueOf(r[0]));
            rec.setNumberOfTransaction(String.valueOf(((Number) r[1]).longValue()));
            rec.setValueOfTransactions(formatAmountFromCents(r[2]));
            out.add(rec);
        }
        return out;
    }

    /** Endpoint #16 — APPROVED transactions only, grouped by merchant. */
    private List<MerchantTransactionRecord> buildMerchantTransactionRecords(Long pspId, String institutionCode,
                                                                             String reportingDate,
                                                                             LocalDateTime start,
                                                                             LocalDateTime end) {
        List<Object[]> rows = transactionRepository.findSuccessfulYesterdayByPspId(pspId, start, end);
        List<MerchantTransactionRecord> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            MerchantTransactionRecord rec = new MerchantTransactionRecord();
            rec.setBankId(institutionCode);
            rec.setReportingDate(reportingDate);
            rec.setMerchantId(r[0] != null ? String.valueOf(r[0]) : "");
            rec.setMerchantAccountNumber(""); // not on TransactionEntity; merchant settlement account lives elsewhere
            rec.setChannelOfSettlement("POS");
            rec.setEmailAddress("");
            rec.setMerchantCountry(String.valueOf(r[1]));
            rec.setEconomicSectors("");
            rec.setNumberOfTransactions(String.valueOf(((Number) r[2]).longValue()));
            rec.setValueOfTransactions(formatAmountFromCents(r[3]));
            out.add(rec);
        }
        return out;
    }

    /** Endpoint #17 — DECLINED + MANUAL_REVIEW (no FAILED literal in TransactionStatus). */
    private List<FailedTransactionRecord> buildFailedTransactionRecords(Long pspId, String institutionCode,
                                                                         String reportingDate,
                                                                         LocalDateTime start,
                                                                         LocalDateTime end) {
        List<Object[]> rows = transactionRepository.findFailedRejectedForPspByDay(pspId, start, end);
        List<FailedTransactionRecord> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            FailedTransactionRecord rec = new FailedTransactionRecord();
            rec.setBankId(institutionCode);
            rec.setReportingDate(reportingDate);
            rec.setCustomerAccountNumber("");
            rec.setChannelOfSettlement("POS");
            rec.setMerchantId(r[0] != null ? String.valueOf(r[0]) : "");
            rec.setEmail("");
            rec.setRejectionFailureReason(String.valueOf(r[1]));
            rec.setNumberOfTransactions(String.valueOf(((Number) r[2]).longValue()));
            rec.setValueOfTransactions(formatAmountFromCents(r[3]));
            out.add(rec);
        }
        return out;
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
