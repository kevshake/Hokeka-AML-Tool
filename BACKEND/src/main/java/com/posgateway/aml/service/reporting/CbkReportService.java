package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.RegulatorySubmissionDTO;
import com.posgateway.aml.dto.reporting.cbk.CbkReportDTO;
import com.posgateway.aml.dto.reporting.cbk.CbkReportDTO.HighValueTransactionSummary;
import com.posgateway.aml.dto.reporting.cbk.CbkReportDTO.MerchantSummary;
import com.posgateway.aml.dto.reporting.cbk.CbkSubmitRequest;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.reporting.Report;
import com.posgateway.aml.entity.reporting.ReportCategory;
import com.posgateway.aml.entity.reporting.ReportType;
import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import com.posgateway.aml.entity.reporting.SubmissionStatus;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CbkReportService {

    private static final Logger logger = LoggerFactory.getLogger(CbkReportService.class);

    // KES 1,000,000 in cents
    private static final long HIGH_VALUE_THRESHOLD_CENTS = 100_000_000L;
    private static final String CBK_REPORT_CODE = "CBK_PERIODIC_REPORT";
    private static final String REGULATOR_CBK = "CBK";
    private static final String JURISDICTION_KE = "KE";

    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final MerchantRepository merchantRepository;
    private final RegulatorySubmissionRepository submissionRepository;
    private final ReportRepository reportRepository;
    private final PspIsolationService pspIsolationService;
    private final CbkExternalGateway cbkExternalGateway;

    public CbkReportService(TransactionRepository transactionRepository,
                             AlertRepository alertRepository,
                             SuspiciousActivityReportRepository sarRepository,
                             MerchantRepository merchantRepository,
                             RegulatorySubmissionRepository submissionRepository,
                             ReportRepository reportRepository,
                             PspIsolationService pspIsolationService,
                             CbkExternalGateway cbkExternalGateway) {
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.sarRepository = sarRepository;
        this.merchantRepository = merchantRepository;
        this.submissionRepository = submissionRepository;
        this.reportRepository = reportRepository;
        this.pspIsolationService = pspIsolationService;
        this.cbkExternalGateway = cbkExternalGateway;
    }

    @Transactional(readOnly = true)
    public CbkReportDTO generateReport(String period, LocalDate fromDate, LocalDate toDate, Long pspId) {
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        logger.info("Generating CBK report for PSP={}, period={}, from={}, to={}", effectivePspId, period, fromDate, toDate);

        CbkReportDTO report = new CbkReportDTO();
        report.setReportId("CBK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        report.setPeriod(period);
        report.setFromDate(fromDate);
        report.setToDate(toDate);
        report.setPspId(effectivePspId);
        report.setGeneratedAt(LocalDateTime.now());

        populateTransactionSummary(report, effectivePspId, fromDateTime, toDateTime);
        populateAlertSummary(report, effectivePspId, fromDateTime, toDateTime);
        populateSarSummary(report, effectivePspId, fromDateTime, toDateTime);
        populateMerchantSummary(report, effectivePspId, fromDateTime, toDateTime);

        return report;
    }

    @Transactional
    public RegulatorySubmissionDTO submitReport(CbkSubmitRequest request, Long userId, Long pspId) {
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId != null ? pspId : request.getPspId());

        CbkReportDTO reportData = generateReport(
                request.getPeriod(), request.getFromDate(), request.getToDate(), effectivePspId);

        Report cbkReport = findOrCreateCbkReport();

        RegulatorySubmission submission = new RegulatorySubmission();
        submission.setSubmissionReference("CBK-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        submission.setReport(cbkReport);
        submission.setRegulatorCode(REGULATOR_CBK);
        submission.setSubmissionType(request.getPeriod().toUpperCase());
        submission.setJurisdiction(JURISDICTION_KE);
        submission.setFilingPeriodStart(request.getFromDate());
        submission.setFilingPeriodEnd(request.getToDate());
        submission.setStatus(SubmissionStatus.PENDING_REVIEW);
        submission.setPspId(effectivePspId);
        submission.setPreparedAt(LocalDateTime.now());

        Map<String, Object> submittedData = buildSubmittedDataMap(reportData, request.getNotes());
        submission.setSubmittedData(submittedData);

        RegulatorySubmission saved = submissionRepository.save(submission);
        logger.info("Created CBK submission {} for PSP={}", saved.getSubmissionReference(), effectivePspId);

        // Outbound CBK reporting must originate from this backend (HOK-130 / HOK-133).
        // The gateway is a no-op when cbk.reporting.base-url is unset, so unconfigured
        // environments stay in PENDING_REVIEW; otherwise the submission transitions to
        // SUBMITTED or REJECTED based on the regulator response.
        if (cbkExternalGateway.isEnabled()) {
            cbkExternalGateway.submit(saved, reportData);
            saved = submissionRepository.save(saved);
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<RegulatorySubmissionDTO> getStatus(Long pspId) {
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);

        Page<RegulatorySubmission> page = submissionRepository.findByFilters(
                effectivePspId, null, REGULATOR_CBK, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        return page.getContent().stream().map(this::toDto).collect(Collectors.toList());
    }

    private void populateTransactionSummary(CbkReportDTO report, Long pspId,
                                             LocalDateTime from, LocalDateTime to) {
        List<TransactionEntity> txns;
        if (pspId != null) {
            txns = transactionRepository.findByPspIdAndTxnTsBetween(pspId, from, to);
        } else {
            txns = transactionRepository.findAll().stream()
                    .filter(t -> t.getTxnTs() != null && !t.getTxnTs().isBefore(from) && !t.getTxnTs().isAfter(to))
                    .collect(Collectors.toList());
        }

        report.setTotalTransactionCount(txns.size());

        long totalCents = txns.stream().mapToLong(t -> t.getAmountCents() != null ? t.getAmountCents() : 0L).sum();
        report.setTotalTransactionVolumeKes(centsToBigDecimal(totalCents));

        report.setApprovedCount(txns.stream().filter(t -> "APPROVED".equalsIgnoreCase(t.getDecision())).count());
        report.setDeclinedCount(txns.stream().filter(t -> "DECLINED".equalsIgnoreCase(t.getDecision())).count());
        report.setManualReviewCount(txns.stream().filter(t -> "MANUAL_REVIEW".equalsIgnoreCase(t.getDecision())).count());

        List<TransactionEntity> highValue = txns.stream()
                .filter(t -> "KES".equalsIgnoreCase(t.getCurrency())
                        && t.getAmountCents() != null
                        && t.getAmountCents() > HIGH_VALUE_THRESHOLD_CENTS)
                .collect(Collectors.toList());

        report.setHighValueTransactionCount(highValue.size());
        long hvTotalCents = highValue.stream().mapToLong(t -> t.getAmountCents() != null ? t.getAmountCents() : 0L).sum();
        report.setHighValueTransactionVolumeKes(centsToBigDecimal(hvTotalCents));

        List<HighValueTransactionSummary> hvSummaries = highValue.stream()
                .sorted(Comparator.comparingLong((TransactionEntity t) -> t.getAmountCents() != null ? t.getAmountCents() : 0L).reversed())
                .limit(100)
                .map(t -> {
                    HighValueTransactionSummary s = new HighValueTransactionSummary();
                    s.setTxnId(t.getTxnId());
                    s.setMerchantId(t.getMerchantId());
                    s.setAmountKes(centsToBigDecimal(t.getAmountCents()));
                    s.setCurrency(t.getCurrency());
                    s.setTxnTs(t.getTxnTs());
                    s.setRiskLevel(t.getRiskLevel());
                    s.setDecision(t.getDecision());
                    return s;
                })
                .collect(Collectors.toList());
        report.setHighValueTransactions(hvSummaries);
    }

    private void populateAlertSummary(CbkReportDTO report, Long pspId,
                                       LocalDateTime from, LocalDateTime to) {
        List<Alert> alerts;
        if (pspId != null) {
            alerts = alertRepository.findByPspIdAndDateRange(pspId, from, to);
        } else {
            alerts = alertRepository.findAlertsInTimeRange(from, to);
        }

        report.setTotalAlertCount(alerts.size());
        report.setOpenAlertCount(alerts.stream().filter(a -> "open".equalsIgnoreCase(a.getStatus())).count());
        report.setClosedAlertCount(alerts.stream().filter(a -> "closed".equalsIgnoreCase(a.getStatus())).count());
        report.setCriticalAlertCount(alerts.stream().filter(a -> "CRITICAL".equalsIgnoreCase(a.getSeverity())).count());
    }

    private void populateSarSummary(CbkReportDTO report, Long pspId,
                                     LocalDateTime from, LocalDateTime to) {
        List<SuspiciousActivityReport> sars;
        if (pspId != null) {
            sars = sarRepository.findByPspIdAndCreatedAtBetween(pspId, from, to);
        } else {
            sars = sarRepository.findByCreatedAtBetween(from, to);
        }

        report.setSarFiledCount(sars.stream().filter(s -> SarStatus.FILED == s.getStatus()).count());
        report.setSarPendingCount(sars.stream().filter(s -> SarStatus.PENDING_REVIEW == s.getStatus()).count());
        report.setSarDraftCount(sars.stream().filter(s -> SarStatus.DRAFT == s.getStatus()).count());
    }

    private void populateMerchantSummary(CbkReportDTO report, Long pspId,
                                          LocalDateTime from, LocalDateTime to) {
        List<Merchant> merchants;
        if (pspId != null) {
            merchants = merchantRepository.findByPspPspId(pspId);
        } else {
            merchants = merchantRepository.findAll();
        }

        report.setActiveMerchantCount(merchants.stream().filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus())).count());

        // Build per-merchant summaries using transactions in range
        List<TransactionEntity> allTxns;
        if (pspId != null) {
            allTxns = transactionRepository.findByPspIdAndTxnTsBetween(pspId, from, to);
        } else {
            allTxns = transactionRepository.findAll().stream()
                    .filter(t -> t.getTxnTs() != null && !t.getTxnTs().isBefore(from) && !t.getTxnTs().isAfter(to))
                    .collect(Collectors.toList());
        }

        Map<String, List<TransactionEntity>> txnsByMerchant = allTxns.stream()
                .filter(t -> t.getMerchantId() != null)
                .collect(Collectors.groupingBy(TransactionEntity::getMerchantId));

        List<Alert> allAlerts;
        if (pspId != null) {
            allAlerts = alertRepository.findByPspIdAndDateRange(pspId, from, to);
        } else {
            allAlerts = alertRepository.findAlertsInTimeRange(from, to);
        }

        Map<Long, Long> alertCountByMerchantId = allAlerts.stream()
                .filter(a -> a.getMerchantId() != null)
                .collect(Collectors.groupingBy(Alert::getMerchantId, Collectors.counting()));

        Map<Long, String> merchantNameById = merchants.stream()
                .collect(Collectors.toMap(Merchant::getMerchantId, m -> m.getLegalName() != null ? m.getLegalName() : ""));

        List<MerchantSummary> summaries = txnsByMerchant.entrySet().stream()
                .map(entry -> {
                    String merchantIdStr = entry.getKey();
                    List<TransactionEntity> merchantTxns = entry.getValue();
                    long totalCents = merchantTxns.stream()
                            .mapToLong(t -> t.getAmountCents() != null ? t.getAmountCents() : 0L).sum();

                    MerchantSummary ms = new MerchantSummary();
                    ms.setMerchantId(merchantIdStr);
                    ms.setMerchantName(resolveMerchantName(merchantIdStr, merchantNameById));
                    ms.setTransactionCount(merchantTxns.size());
                    ms.setTransactionVolumeKes(centsToBigDecimal(totalCents));

                    // Alert count: try to match by merchant string ID -> Long ID
                    long alertCount = 0;
                    try {
                        Long merchantIdLong = Long.parseLong(merchantIdStr);
                        alertCount = alertCountByMerchantId.getOrDefault(merchantIdLong, 0L);
                    } catch (NumberFormatException ignored) {
                        // merchant_id in transactions is a string code, not always a Long
                    }
                    ms.setAlertCount(alertCount);
                    return ms;
                })
                .sorted(Comparator.comparing(MerchantSummary::getTransactionVolumeKes).reversed())
                .limit(50)
                .collect(Collectors.toList());

        report.setMerchantSummaries(summaries);
    }

    private String resolveMerchantName(String merchantIdStr, Map<Long, String> merchantNameById) {
        try {
            Long id = Long.parseLong(merchantIdStr);
            return merchantNameById.getOrDefault(id, merchantIdStr);
        } catch (NumberFormatException e) {
            return merchantIdStr;
        }
    }

    private Report findOrCreateCbkReport() {
        return reportRepository.findByReportCode(CBK_REPORT_CODE).orElseGet(() -> {
            Report r = new Report();
            r.setReportCode(CBK_REPORT_CODE);
            r.setReportName("CBK Periodic Regulatory Report");
            r.setReportCategory(ReportCategory.REGULATORY_SUBMISSION);
            r.setReportType(ReportType.REGULATORY);
            r.setDescription("Kenya Central Bank (CBK) periodic regulatory reporting: transactions, alerts, STRs, and merchant summaries");
            r.setRegulatoryTemplate("CBK");
            r.setRequiresApproval(true);
            r.setEnabled(true);
            return reportRepository.save(r);
        });
    }

    private Map<String, Object> buildSubmittedDataMap(CbkReportDTO report, String notes) {
        Map<String, Object> data = new HashMap<>();
        data.put("reportId", report.getReportId());
        data.put("period", report.getPeriod());
        data.put("fromDate", report.getFromDate().toString());
        data.put("toDate", report.getToDate().toString());
        data.put("generatedAt", report.getGeneratedAt().toString());
        data.put("totalTransactionCount", report.getTotalTransactionCount());
        data.put("totalTransactionVolumeKes", report.getTotalTransactionVolumeKes());
        data.put("highValueTransactionCount", report.getHighValueTransactionCount());
        data.put("totalAlertCount", report.getTotalAlertCount());
        data.put("sarFiledCount", report.getSarFiledCount());
        data.put("activeMerchantCount", report.getActiveMerchantCount());
        if (notes != null && !notes.isEmpty()) {
            data.put("notes", notes);
        }
        return data;
    }

    private RegulatorySubmissionDTO toDto(RegulatorySubmission s) {
        RegulatorySubmissionDTO dto = new RegulatorySubmissionDTO();
        dto.setId(s.getId());
        dto.setSubmissionReference(s.getSubmissionReference());
        if (s.getReport() != null) {
            dto.setReportId(s.getReport().getId());
            dto.setReportCode(s.getReport().getReportCode());
            dto.setReportName(s.getReport().getReportName());
        }
        dto.setRegulatorCode(s.getRegulatorCode());
        dto.setSubmissionType(s.getSubmissionType());
        dto.setJurisdiction(s.getJurisdiction());
        dto.setFilingPeriodStart(s.getFilingPeriodStart());
        dto.setFilingPeriodEnd(s.getFilingPeriodEnd());
        dto.setFilingDeadline(s.getFilingDeadline());
        dto.setStatus(s.getStatus());
        dto.setPspId(s.getPspId());
        dto.setSubmittedData(s.getSubmittedData());
        dto.setPreparedAt(s.getPreparedAt());
        dto.setFiledAt(s.getFiledAt());
        dto.setRegulatorReference(s.getRegulatorReference());
        dto.setIsLateFiling(s.isLateFiling());
        dto.setDaysUntilDeadline(s.getDaysUntilDeadline());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    private BigDecimal centsToBigDecimal(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
