package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.dto.analytics.CountryRiskDTO;
import com.posgateway.aml.dto.analytics.TopRiskMerchantDTO;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.ModelMetrics;
import com.posgateway.aml.entity.merchant.MerchantScreeningResult;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import com.posgateway.aml.repository.ModelMetricsRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;

// @RequiredArgsConstructor removed
@PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final MerchantRepository merchantRepository;
    private final ComplianceCaseRepository caseRepository;
    private final com.posgateway.aml.repository.UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final MerchantScreeningResultRepository screeningResultRepository;
    private final ModelMetricsRepository modelMetricsRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final HighRiskCountryRepository highRiskCountryRepository;

    /**
     * Transactions are considered "flagged" when their stored risk score
     * (TRS) is &ge; this threshold. Aligned with the risk-level cutoff used
     * across the platform (HIGH band starts at 70).
     */
    private static final double FLAGGED_RISK_THRESHOLD = 70.0;

    /** SAR-filing SLA — regulators expect filing within 30 days of case open. */
    private static final int SAR_FILING_SLA_DAYS = 30;

    /** Non-terminal case statuses counted as active/open in KPIs and nav badges. */
    private static final List<CaseStatus> ACTIVE_CASE_STATUSES = List.of(
            CaseStatus.NEW,
            CaseStatus.ASSIGNED,
            CaseStatus.IN_PROGRESS,
            CaseStatus.PENDING_INFO,
            CaseStatus.PENDING_REVIEW,
            CaseStatus.ESCALATED,
            CaseStatus.REOPENED);

    public DashboardController(MerchantRepository merchantRepository, ComplianceCaseRepository caseRepository,
            com.posgateway.aml.repository.UserRepository userRepository,
            TransactionRepository transactionRepository,
            AlertRepository alertRepository,
            MerchantScreeningResultRepository screeningResultRepository,
            ModelMetricsRepository modelMetricsRepository,
            SuspiciousActivityReportRepository sarRepository,
            HighRiskCountryRepository highRiskCountryRepository) {
        this.merchantRepository = merchantRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.modelMetricsRepository = modelMetricsRepository;
        this.sarRepository = sarRepository;
        this.highRiskCountryRepository = highRiskCountryRepository;
    }

    public com.posgateway.aml.entity.User getCurrentUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null)
            return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> stats = new HashMap<>();
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;

        if (pspId != null) {
            // Single GROUP BY query replaces 3 separate COUNT queries for merchant stats
            Map<String, Long> merchantCounts = rowsToMap(merchantRepository.countByPspIdGroupByStatus(pspId));
            stats.put("totalMerchants", merchantCounts.values().stream().mapToLong(Long::longValue).sum());
            stats.put("activeMerchants", merchantCounts.getOrDefault("ACTIVE", 0L));
            stats.put("pendingScreening", merchantCounts.getOrDefault("PENDING_SCREENING", 0L));

            // Single GROUP BY query replaces 3 separate COUNT queries for case stats
            Map<String, Long> caseCounts = rowsToMap(caseRepository.countByPspIdGroupByStatus(pspId));
            stats.put("openCases", sumActiveCases(caseCounts));
        } else {
            Map<String, Long> merchantCounts = rowsToMap(merchantRepository.countAllGroupByStatus());
            stats.put("totalMerchants", merchantCounts.values().stream().mapToLong(Long::longValue).sum());
            stats.put("activeMerchants", merchantCounts.getOrDefault("ACTIVE", 0L));
            stats.put("pendingScreening", merchantCounts.getOrDefault("PENDING_SCREENING", 0L));

            Map<String, Long> caseCounts = rowsToMap(caseRepository.countAllGroupByStatus());
            stats.put("openCases", sumActiveCases(caseCounts));
        }
        stats.put("urgentCases", 0L);

        // --- Extended KPI fields (Phase 4) ---------------------------------
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfYesterday = startOfToday.minusDays(1);

        // Flagged transactions today
        long flaggedToday = pspId != null
                ? transactionRepository.countFlaggedInPeriodByPsp(pspId, startOfToday, now, FLAGGED_RISK_THRESHOLD)
                : transactionRepository.countFlaggedInPeriod(startOfToday, now, FLAGGED_RISK_THRESHOLD);
        long flaggedYesterday = pspId != null
                ? transactionRepository.countFlaggedInPeriodByPsp(pspId, startOfYesterday, startOfToday, FLAGGED_RISK_THRESHOLD)
                : transactionRepository.countFlaggedInPeriod(startOfYesterday, startOfToday, FLAGGED_RISK_THRESHOLD);
        stats.put("flaggedToday", flaggedToday);

        long openAlerts = pspId != null
                ? alertRepository.countOpenByPspId(pspId)
                : alertRepository.countByStatus("open");
        stats.put("openAlertsCount", openAlerts);

        // High-risk customers (merchants with riskLevel=HIGH)
        long highRiskMerchants = pspId != null
                ? merchantRepository.countByPspPspIdAndRiskLevel(pspId, "HIGH")
                : merchantRepository.countByRiskLevel("HIGH");
        stats.put("highRiskCustomerCount", highRiskMerchants);

        // Compliance Health composite score
        Map<String, Integer> health = computeComplianceHealthMap(pspId);
        int complianceHealthScore = (health.get("kycCompletion")
                + health.get("cddReviews")
                + health.get("eddReviews")
                + health.get("sarFilingSla")) / 4;
        stats.put("complianceHealthScore", complianceHealthScore);

        // Trends — yesterday vs today
        long totalTxnToday = pspId != null
                ? transactionRepository.countByPspAndPeriod(pspId, startOfToday, now)
                : transactionRepository.countByPspAndPeriod(null, startOfToday, now);
        stats.put("transactionsMonitoredToday", totalTxnToday);

        long totalTxnYesterday = pspId != null
                ? transactionRepository.countByPspAndPeriod(pspId, startOfYesterday, startOfToday)
                : transactionRepository.countByPspAndPeriod(null, startOfYesterday, startOfToday);

        long screeningMatchesToday = screeningResultRepository
                .countByScreeningStatusAndScreenedAtAfter("MATCH", startOfToday)
                + screeningResultRepository.countByScreeningStatusAndScreenedAtAfter("POTENTIAL_MATCH", startOfToday);
        long screeningMatchesYesterday = screeningResultRepository
                .countByScreeningStatusAndScreenedAtAfter("MATCH", startOfYesterday)
                + screeningResultRepository.countByScreeningStatusAndScreenedAtAfter("POTENTIAL_MATCH", startOfYesterday)
                - screeningMatchesToday;
        if (screeningMatchesYesterday < 0) screeningMatchesYesterday = 0;

        // For openCases and highRiskMerchants we don't have a cheap historical
        // snapshot, so deltas are reported as 0 (no fabrication). The frontend
        // shows these as neutral. If/when audit history is wired in, swap to
        // a snapshot-based query.
        Map<String, Double> trends = new HashMap<>();
        trends.put("totalTransactionsDelta", percentDelta(totalTxnToday, totalTxnYesterday));
        trends.put("flaggedDelta", percentDelta(flaggedToday, flaggedYesterday));
        trends.put("openCasesDelta", 0.0);
        trends.put("highRiskCustomersDelta", 0.0);
        trends.put("screeningMatchesDelta", percentDelta(screeningMatchesToday, screeningMatchesYesterday));
        trends.put("complianceHealthDelta", 0.0);
        stats.put("trends", trends);

        return ResponseEntity.ok(stats);
    }

    private static double percentDelta(long today, long yesterday) {
        if (yesterday <= 0) return 0.0;
        double pct = ((double) (today - yesterday) / (double) yesterday) * 100.0;
        return Math.round(pct * 10.0) / 10.0;
    }

    private Map<String, Long> rowsToMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                map.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        return map;
    }

    private long sumActiveCases(Map<String, Long> caseCounts) {
        return ACTIVE_CASE_STATUSES.stream()
                .mapToLong(s -> caseCounts.getOrDefault(s.name(), 0L))
                .sum();
    }

    @GetMapping("/cases/priority")
    public ResponseEntity<Map<String, Long>> getCasesByPriority() {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;
        // Single GROUP BY replaces N per-priority COUNT queries
        List<Object[]> rows = pspId != null
                ? caseRepository.countByPspIdGroupByPriority(pspId)
                : caseRepository.countAllGroupByPriority();
        return ResponseEntity.ok(rowsToMap(rows));
    }

    /**
     * Merchant-filtered case stats
     * GET /api/v1/dashboard/cases/merchant/{merchantId}
     */
    @GetMapping("/cases/merchant/{merchantId}")
    public ResponseEntity<Map<String, Long>> getCasesByMerchant(@PathVariable Long merchantId) {
        Map<String, Long> stats = new HashMap<>();
        for (com.posgateway.aml.model.CaseStatus s : com.posgateway.aml.model.CaseStatus.values()) {
            stats.put("status_" + s.name(), caseRepository.countByMerchantIdAndStatus(merchantId, s));
        }
        for (com.posgateway.aml.model.CasePriority p : com.posgateway.aml.model.CasePriority.values()) {
            stats.put("priority_" + p.name(), caseRepository.countByMerchantIdAndPriority(merchantId, p));
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/risk-distribution")
    public ResponseEntity<Map<String, Long>> getRiskDistribution() {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;
        // Single GROUP BY replaces 4 separate per-risk-level COUNT queries
        List<Object[]> rows = pspId != null
                ? merchantRepository.countByPspIdGroupByRiskLevel(pspId)
                : merchantRepository.countAllGroupByRiskLevel();
        return ResponseEntity.ok(rowsToMap(rows));
    }

    @GetMapping("/sanctions/status")
    public ResponseEntity<Map<String, Object>> getSanctionsStatus() {
        Map<String, Object> status = new HashMap<>();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Optional<MerchantScreeningResult> latestResult = screeningResultRepository.findLatestScreeningResult();
        if (latestResult.isPresent()) {
            LocalDateTime screenedAt = latestResult.get().getScreenedAt();
            status.put("lastRun", screenedAt.toString());
            boolean stale = screenedAt.isBefore(LocalDateTime.now().minusHours(24));
            status.put("status", stale ? "STALE" : "SUCCESS");
        } else {
            status.put("lastRun", "N/A");
            status.put("status", "NO_DATA");
        }

        long merchantsProcessed = screeningResultRepository.countScreenedSince(startOfDay);
        long hitsFound = screeningResultRepository.countByScreeningStatusAndScreenedAtAfter("MATCH", startOfDay)
                + screeningResultRepository.countByScreeningStatusAndScreenedAtAfter("POTENTIAL_MATCH", startOfDay);

        status.put("merchantsProcessed", merchantsProcessed);
        status.put("hitsFound", hitsFound);

        return ResponseEntity.ok(status);
    }

    @GetMapping("/fraud-metrics")
    public ResponseEntity<Map<String, Object>> getFraudMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // AUC from the latest stored model metrics row
        Optional<ModelMetrics> latestMetrics = modelMetricsRepository.findFirstByOrderByDateDesc();
        if (latestMetrics.isPresent()) {
            ModelMetrics m = latestMetrics.get();
            if (m.getAuc() != null) {
                metrics.put("auc", formatPct(m.getAuc() * 100.0));
            }
            if (m.getPrecisionAt100() != null) {
                metrics.put("precisionAt100", formatPct(m.getPrecisionAt100() * 100.0));
            }
            if (m.getDriftScore() != null) {
                metrics.put("driftScore", String.format("%.4f", m.getDriftScore()));
            }
            if (m.getAvgLatencyMs() != null) {
                metrics.put("avgLatencyMs", String.format("%.1f ms", m.getAvgLatencyMs()));
            }
            metrics.put("modelDate", m.getDate().toString());
        }

        // Precision, recall, F1, and false-positive rate computed from alert disposition outcomes
        List<AlertDisposition> truePositiveDispositions = Arrays.asList(
                AlertDisposition.TRUE_POSITIVE_SAR_FILED,
                AlertDisposition.TRUE_POSITIVE_BLOCKED,
                AlertDisposition.TRUE_POSITIVE_REPORTED);
        List<AlertDisposition> falsePositiveDispositions = Arrays.asList(
                AlertDisposition.FALSE_POSITIVE,
                AlertDisposition.DUPLICATE,
                AlertDisposition.TECHNICAL_ERROR);

        long truePositives = alertRepository.countByDispositionIn(truePositiveDispositions);
        long falsePositives = alertRepository.countByDispositionIn(falsePositiveDispositions);
        long reviewedAlerts = alertRepository.countReviewedAlerts();
        long totalAlerts = alertRepository.count();

        if (truePositives + falsePositives > 0) {
            double precision = (double) truePositives / (truePositives + falsePositives) * 100.0;
            metrics.put("precision", formatPct(precision));
        } else {
            metrics.put("precision", "N/A");
        }

        if (reviewedAlerts > 0) {
            double recall = (double) truePositives / reviewedAlerts * 100.0;
            metrics.put("recall", formatPct(recall));

            double precisionRaw = (truePositives + falsePositives > 0)
                    ? (double) truePositives / (truePositives + falsePositives)
                    : 0.0;
            double recallRaw = (double) truePositives / reviewedAlerts;
            if (precisionRaw + recallRaw > 0) {
                double f1 = 2.0 * precisionRaw * recallRaw / (precisionRaw + recallRaw) * 100.0;
                metrics.put("f1", formatPct(f1));
            } else {
                metrics.put("f1", "N/A");
            }
        } else {
            metrics.put("recall", "N/A");
            metrics.put("f1", "N/A");
        }

        if (totalAlerts > 0) {
            double fpRate = (double) falsePositives / totalAlerts * 100.0;
            metrics.put("falsePositiveRate", formatPct(fpRate));
        } else {
            metrics.put("falsePositiveRate", "N/A");
        }

        metrics.put("truePositiveCount", truePositives);
        metrics.put("falsePositiveCount", falsePositives);
        metrics.put("reviewedAlertCount", reviewedAlerts);
        metrics.put("totalAlertCount", totalAlerts);

        return ResponseEntity.ok(metrics);
    }

    private String formatPct(double value) {
        return String.format("%.1f%%", value);
    }

    /**
     * Get daily transaction volume for the last N days
     * GET /api/v1/dashboard/transaction-volume?days=7
     */
    @GetMapping("/transaction-volume")
    public ResponseEntity<Map<String, Object>> getDailyTransactionVolume(
            @RequestParam(defaultValue = "7") int days) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;

        // Calculate date range (inclusive of today)
        // For 7 days: today + 6 previous days = 7 days total
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.withHour(23).withMinute(59).withSecond(59).plusDays(1); // Exclusive end for query
        LocalDateTime startDate = now.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0);

        List<Object[]> results;
        if (pspId != null) {
            // Get data filtered by PSP
            results = transactionRepository.getDailyTransactionCountByPspId(pspId, startDate, endDate);
        } else {
            // Admin view - all PSPs
            results = transactionRepository.getDailyTransactionCountAll(startDate, endDate);
        }

        // Build response with labels and data arrays
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        // Create a map of date -> count from query results
        Map<LocalDate, Long> dateCountMap = new HashMap<>();
        for (Object[] row : results) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            Long count = ((Number) row[1]).longValue();
            dateCountMap.put(date, count);
        }

        // Fill in all dates in range (including days with 0 transactions)
        LocalDate currentDate = startDate.toLocalDate();
        LocalDate endDateLocal = endDate.toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d");

        while (!currentDate.isAfter(endDateLocal)) {
            labels.add(currentDate.format(formatter));
            data.add(dateCountMap.getOrDefault(currentDate, 0L));
            currentDate = currentDate.plusDays(1);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("labels", labels);
        response.put("data", data);
        response.put("pspId", pspId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get recent live alerts for dashboard
     * GET /api/v1/dashboard/live-alerts?limit=5
     */
    @GetMapping("/live-alerts")
    public ResponseEntity<List<Alert>> getLiveAlerts(
            @RequestParam(defaultValue = "5") int limit) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;

        List<Alert> alerts;
        if (pspId != null) {
            // Get alerts filtered by PSP
            alerts = alertRepository.findRecentOpenAlertsByPspId(pspId, limit);
        } else {
            // Admin view - all PSPs, limit results
            alerts = alertRepository.findRecentOpenAlerts();
            if (alerts.size() > limit) {
                alerts = alerts.subList(0, limit);
            }
        }

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get recent transactions for dashboard activity timeline
     * GET /api/v1/dashboard/recent-transactions?limit=5
     */
    @GetMapping("/recent-transactions")
    public ResponseEntity<List<com.posgateway.aml.entity.TransactionEntity>> getRecentTransactions(
            @RequestParam(defaultValue = "5") int limit) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, limit,
                        org.springframework.data.domain.Sort.by("txnTs").descending());
        // Push LIMIT into the query; avoids loading full table into memory
        List<com.posgateway.aml.entity.TransactionEntity> transactions = pspId != null
                ? transactionRepository.findByPspId(pspId, pageable).getContent()
                : transactionRepository.findAll(pageable).getContent();
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get case aging distribution
     * GET /api/v1/dashboard/case-aging
     */
    @GetMapping("/case-aging")
    public ResponseEntity<Map<String, Object>> getCaseAging() {
        List<com.posgateway.aml.entity.compliance.ComplianceCase> openCases = 
            caseRepository.findByStatusIn(java.util.List.of(
                com.posgateway.aml.model.CaseStatus.NEW,
                com.posgateway.aml.model.CaseStatus.ASSIGNED,
                com.posgateway.aml.model.CaseStatus.IN_PROGRESS,
                com.posgateway.aml.model.CaseStatus.PENDING_REVIEW,
                com.posgateway.aml.model.CaseStatus.ESCALATED
            ));
        
        Map<String, Long> agingDistribution = new HashMap<>();
        agingDistribution.put("0-7", 0L);
        agingDistribution.put("8-14", 0L);
        agingDistribution.put("15-30", 0L);
        agingDistribution.put("31-60", 0L);
        agingDistribution.put("60+", 0L);
        
        for (com.posgateway.aml.entity.compliance.ComplianceCase c : openCases) {
            int daysOpen = c.getDaysOpen() != null ? c.getDaysOpen() : 0;
            if (daysOpen <= 7) {
                agingDistribution.put("0-7", agingDistribution.get("0-7") + 1);
            } else if (daysOpen <= 14) {
                agingDistribution.put("8-14", agingDistribution.get("8-14") + 1);
            } else if (daysOpen <= 30) {
                agingDistribution.put("15-30", agingDistribution.get("15-30") + 1);
            } else if (daysOpen <= 60) {
                agingDistribution.put("31-60", agingDistribution.get("31-60") + 1);
            } else {
                agingDistribution.put("60+", agingDistribution.get("60+") + 1);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("agingDistribution", agingDistribution);
        response.put("totalOpenCases", openCases.size());

        return ResponseEntity.ok(response);
    }

    // =======================================================================
    // Phase 4 dashboard aggregates
    // =======================================================================

    /**
     * Per-country transaction + alert aggregate for the risk heatmap.
     * GET /api/v1/dashboard/risk-heatmap
     */
    @GetMapping("/risk-heatmap")
    @Cacheable(value = "dashboard-kpis",
            key = "'risk-heatmap:' + T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication()?.name")
    public ResponseEntity<List<CountryRiskDTO>> getRiskHeatmap() {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;

        final int limit = 60;
        List<Object[]> rows = pspId != null
                ? transactionRepository.getCountryTransactionAndAlertCountsByPsp(pspId, limit)
                : transactionRepository.getCountryTransactionAndAlertCounts(limit);

        List<CountryRiskDTO> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String code = row[0] != null ? row[0].toString() : "XXX";
            long txnCount = ((Number) row[1]).longValue();
            long alertCount = ((Number) row[2]).longValue();
            String countryName = highRiskCountryRepository.findByCountryCode(code)
                    .map(c -> c.getCountryName() != null ? c.getCountryName() : code)
                    .orElse(code);
            boolean inHighRiskList = highRiskCountryRepository.existsByCountryCode(code);
            double ratio = txnCount > 0 ? ((double) alertCount / (double) txnCount) : 0.0;
            String level;
            if (ratio > 0.10 || inHighRiskList) {
                level = "HIGH";
            } else if (ratio > 0.03) {
                level = "MEDIUM";
            } else {
                level = "LOW";
            }
            out.add(new CountryRiskDTO(code, countryName, level, txnCount, alertCount));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Recent-closure metrics for the Investigation Cases widget.
     * GET /api/v1/dashboard/cases/closed-recent
     */
    @GetMapping("/cases/closed-recent")
    @Cacheable(value = "dashboard-kpis", key = "'closed-recent'")
    public ResponseEntity<Map<String, Object>> getClosedRecent() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startCurrent = now.minusDays(30);
        LocalDateTime startPrevious = now.minusDays(60);

        // CaseStatus enum has no FALSE_POSITIVE literal — CLOSED_CLEARED is the
        // canonical "false positive / cleared" disposition.
        List<CaseStatus> resolvedStatuses = List.of(
                CaseStatus.CLOSED_CLEARED,
                CaseStatus.CLOSED_SAR_FILED,
                CaseStatus.CLOSED_BLOCKED,
                CaseStatus.CLOSED_REJECTED);
        List<CaseStatus> falsePositiveStatuses = List.of(CaseStatus.CLOSED_CLEARED);
        List<CaseStatus> openStatuses = List.of(
                CaseStatus.NEW,
                CaseStatus.ASSIGNED,
                CaseStatus.IN_PROGRESS,
                CaseStatus.PENDING_INFO,
                CaseStatus.PENDING_REVIEW,
                CaseStatus.ESCALATED);

        long resolved = caseRepository.countByStatusInAndResolvedAtBetween(resolvedStatuses, startCurrent, now);
        long falsePositive = caseRepository.countByStatusInAndResolvedAtBetween(falsePositiveStatuses, startCurrent, now);
        long open = caseRepository.countByStatusIn(openStatuses);

        double closureRate = (resolved + open) > 0
                ? ((double) resolved / (double) (resolved + open)) * 100.0
                : 0.0;
        closureRate = Math.round(closureRate * 10.0) / 10.0;

        long resolvedPrev = caseRepository.countByStatusInAndResolvedAtBetween(resolvedStatuses, startPrevious, startCurrent);
        double prevRate = (resolvedPrev + open) > 0
                ? ((double) resolvedPrev / (double) (resolvedPrev + open)) * 100.0
                : 0.0;
        double closureRateTrend = Math.round((closureRate - prevRate) * 10.0) / 10.0;

        // 7-day sparkline of closed-case counts
        LocalDateTime sparkStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<Object[]> daily = caseRepository.getDailyResolvedCounts(sparkStart, now);
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : daily) {
            LocalDate d = ((java.sql.Date) row[0]).toLocalDate();
            byDay.put(d, ((Number) row[1]).longValue());
        }
        List<Integer> sparkline = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            sparkline.add(byDay.getOrDefault(d, 0L).intValue());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("resolved", resolved);
        body.put("falsePositive", falsePositive);
        body.put("closureRate", closureRate);
        body.put("closureRateTrend", closureRateTrend);
        body.put("sparkline", sparkline);
        return ResponseEntity.ok(body);
    }

    /**
     * Today's screening-result breakdown by hit list type.
     * GET /api/v1/dashboard/screening/results-today
     */
    @GetMapping("/screening/results-today")
    @Cacheable(value = "dashboard-kpis", key = "'screening-today'")
    public ResponseEntity<Map<String, Long>> getScreeningResultsToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<Object[]> rows = screeningResultRepository.countTodayByHitListType(startOfDay);

        long pep = 0, sanctions = 0, adverse = 0, watchlist = 0;
        for (Object[] row : rows) {
            String type = row[0] != null ? row[0].toString().toUpperCase() : "UNKNOWN";
            long cnt = ((Number) row[1]).longValue();
            if (type.contains("PEP")) {
                pep += cnt;
            } else if (type.contains("SANCTION")) {
                sanctions += cnt;
            } else if (type.contains("ADVERSE") || type.contains("MEDIA")) {
                adverse += cnt;
            } else if (type.contains("WATCH")) {
                watchlist += cnt;
            } else {
                // Unknown hitListType — bucket as sanctions (the most common
                // category) rather than fabricate a category. Pragmatic
                // default to avoid silently dropping real matches.
                sanctions += cnt;
            }
        }
        Map<String, Long> body = new HashMap<>();
        body.put("pepMatches", pep);
        body.put("sanctionsMatches", sanctions);
        body.put("adverseMediaHits", adverse);
        body.put("watchlistMatches", watchlist);
        return ResponseEntity.ok(body);
    }

    /**
     * Top-N highest-risk merchants for the dashboard.
     * GET /api/v1/dashboard/merchants/top-risk?limit=5
     */
    @GetMapping("/merchants/top-risk")
    public ResponseEntity<List<TopRiskMerchantDTO>> getTopRiskMerchants(
            @RequestParam(defaultValue = "5") int limit) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;
        if (limit <= 0) limit = 5;
        if (limit > 50) limit = 50;

        List<Object[]> rows = pspId != null
                ? merchantRepository.findTopRiskMerchantsByPsp(pspId, limit)
                : merchantRepository.findTopRiskMerchants(limit);

        List<TopRiskMerchantDTO> out = new ArrayList<>(rows.size());
        int rank = 1;
        for (Object[] row : rows) {
            Long merchantId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String legalName = row[1] != null ? row[1].toString() : null;
            String tradingName = row[2] != null ? row[2].toString() : null;
            Double krs = row[3] != null ? ((Number) row[3]).doubleValue() : null;
            String riskLevel = row[4] != null ? row[4].toString() : "UNKNOWN";
            String name = (legalName != null && !legalName.isBlank()) ? legalName : tradingName;
            out.add(new TopRiskMerchantDTO(rank++, merchantId, name, krs, riskLevel));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Compliance Health aggregate.
     * GET /api/v1/dashboard/compliance/health
     */
    @GetMapping("/compliance/health")
    @Cacheable(value = "dashboard-kpis",
            key = "'compliance-health:' + T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication()?.name")
    public ResponseEntity<Map<String, Integer>> getComplianceHealth() {
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;
        return ResponseEntity.ok(computeComplianceHealthMap(pspId));
    }

    private Map<String, Integer> computeComplianceHealthMap(Long pspId) {
        long totalMerchants = pspId != null
                ? merchantRepository.countAllMerchantsByPsp(pspId)
                : merchantRepository.countAllMerchants();
        long approvedKyc = pspId != null
                ? merchantRepository.countByPspPspIdAndKycStatus(pspId, "APPROVED")
                : merchantRepository.countByKycStatus("APPROVED");

        int kycCompletion = totalMerchants > 0
                ? (int) Math.round(((double) approvedKyc / (double) totalMerchants) * 100.0)
                : 0;

        // CDD: last_cdd_review_at within 12 months
        LocalDateTime cddCutoff = LocalDateTime.now().minusMonths(12);
        long cddReviewed = pspId != null
                ? merchantRepository.countWithCddReviewSinceByPsp(pspId, cddCutoff)
                : merchantRepository.countWithCddReviewSince(cddCutoff);
        int cddReviews = totalMerchants > 0
                ? (int) Math.round(((double) cddReviewed / (double) totalMerchants) * 100.0)
                : 0;

        // EDD: HIGH-risk merchants with last_edd_review_at within 6 months
        LocalDateTime eddCutoff = LocalDateTime.now().minusMonths(6);
        long highRisk = pspId != null
                ? merchantRepository.countHighRiskMerchantsByPsp(pspId)
                : merchantRepository.countHighRiskMerchants();
        long eddReviewed = pspId != null
                ? merchantRepository.countHighRiskWithEddReviewSinceByPsp(pspId, eddCutoff)
                : merchantRepository.countHighRiskWithEddReviewSince(eddCutoff);
        int eddReviews = highRisk > 0
                ? (int) Math.round(((double) eddReviewed / (double) highRisk) * 100.0)
                : 0;

        // SAR filing SLA: % filed within SAR_FILING_SLA_DAYS of case creation.
        // Window: last 12 months of filed SARs.
        LocalDateTime sarSince = LocalDateTime.now().minusMonths(12);
        long sarsFiled = sarRepository.countFiledSince(sarSince);
        long sarsFiledInSla = sarRepository.countFiledWithinSla(sarSince, SAR_FILING_SLA_DAYS);
        int sarFilingSla = sarsFiled > 0
                ? (int) Math.round(((double) sarsFiledInSla / (double) sarsFiled) * 100.0)
                : 0;

        Map<String, Integer> out = new HashMap<>();
        out.put("kycCompletion", kycCompletion);
        out.put("cddReviews", cddReviews);
        out.put("eddReviews", eddReviews);
        out.put("sarFilingSla", sarFilingSla);
        return out;
    }

    /**
     * Daily alert-count trend for the Alert Trends widget.
     * GET /api/v1/dashboard/alerts/trends?days=7
     */
    @GetMapping("/alerts/trends")
    public ResponseEntity<Map<String, Object>> getAlertTrends(
            @RequestParam(defaultValue = "7") int days) {
        if (days <= 0) days = 7;
        if (days > 90) days = 90;
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endExclusive = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();

        List<Object[]> rows = pspId != null
                ? alertRepository.getDailyAlertCountsByPsp(pspId, start, endExclusive)
                : alertRepository.getDailyAlertCounts(start, endExclusive);

        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate d = ((java.sql.Date) row[0]).toLocalDate();
            byDay.put(d, ((Number) row[1]).longValue());
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");
        List<String> labels = new ArrayList<>(days);
        List<Integer> data = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            labels.add(d.format(fmt));
            data.add(byDay.getOrDefault(d, 0L).intValue());
        }
        // Reference `now` to keep static analyzers quiet about the unused
        // local — it documents the snapshot timestamp of this aggregate.
        Map<String, Object> body = new HashMap<>();
        body.put("labels", labels);
        body.put("data", data);
        body.put("asOf", now.toString());
        return ResponseEntity.ok(body);
    }

    /**
     * Daily new-case counts for the Active Cases KPI sparkline.
     * GET /api/v1/dashboard/cases/trends?days=7
     */
    @GetMapping("/cases/trends")
    public ResponseEntity<Map<String, Object>> getCaseTrends(
            @RequestParam(defaultValue = "7") int days) {
        if (days <= 0) days = 7;
        if (days > 90) days = 90;
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;

        LocalDateTime endExclusive = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();

        List<Object[]> rows = pspId != null
                ? caseRepository.getDailyCreatedCountsByPsp(pspId, start, endExclusive)
                : caseRepository.getDailyCreatedCounts(start, endExclusive);

        return ResponseEntity.ok(buildDailyTrendResponse(days, rows));
    }

    /**
     * Daily screening-match counts for the Watchlist Matches KPI sparkline.
     * GET /api/v1/dashboard/screening/matches-trends?days=7
     */
    @GetMapping("/screening/matches-trends")
    public ResponseEntity<Map<String, Object>> getScreeningMatchTrends(
            @RequestParam(defaultValue = "7") int days) {
        if (days <= 0) days = 7;
        if (days > 90) days = 90;

        LocalDateTime endExclusive = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();

        List<Object[]> rows = screeningResultRepository.getDailyMatchCounts(start, endExclusive);
        return ResponseEntity.ok(buildDailyTrendResponse(days, rows));
    }

    /**
     * Daily high-risk merchant activity for the High Risk Customers KPI sparkline.
     * GET /api/v1/dashboard/merchants/high-risk-trends?days=7
     */
    @GetMapping("/merchants/high-risk-trends")
    public ResponseEntity<Map<String, Object>> getHighRiskTrends(
            @RequestParam(defaultValue = "7") int days) {
        if (days <= 0) days = 7;
        if (days > 90) days = 90;
        com.posgateway.aml.entity.User user = getCurrentUser();
        Long pspId = (user != null && user.getPsp() != null) ? user.getPsp().getPspId() : null;

        LocalDateTime endExclusive = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();

        List<Object[]> rows = pspId != null
                ? merchantRepository.getDailyHighRiskActivityCountsByPsp(pspId, start, endExclusive)
                : merchantRepository.getDailyHighRiskActivityCounts(start, endExclusive);

        return ResponseEntity.ok(buildDailyTrendResponse(days, rows));
    }

    private Map<String, Object> buildDailyTrendResponse(int days, List<Object[]> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate d = ((java.sql.Date) row[0]).toLocalDate();
            byDay.put(d, ((Number) row[1]).longValue());
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        List<String> labels = new ArrayList<>(days);
        List<Integer> data = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            labels.add(d.format(fmt));
            data.add(byDay.getOrDefault(d, 0L).intValue());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("labels", labels);
        body.put("data", data);
        return body;
    }
}
