package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final MerchantRepository merchantRepository;
    private final ComplianceCaseRepository caseRepository;
    private final com.posgateway.aml.repository.UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;

    public DashboardController(MerchantRepository merchantRepository, ComplianceCaseRepository caseRepository,
            com.posgateway.aml.repository.UserRepository userRepository,
            TransactionRepository transactionRepository,
            AlertRepository alertRepository) {
        this.merchantRepository = merchantRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
    }

    private com.posgateway.aml.entity.User getCurrentUser() {
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
            stats.put("openCases",
                    caseCounts.getOrDefault("NEW", 0L)
                    + caseCounts.getOrDefault("ASSIGNED", 0L)
                    + caseCounts.getOrDefault("IN_PROGRESS", 0L));
        } else {
            Map<String, Long> merchantCounts = rowsToMap(merchantRepository.countAllGroupByStatus());
            stats.put("totalMerchants", merchantCounts.values().stream().mapToLong(Long::longValue).sum());
            stats.put("activeMerchants", merchantCounts.getOrDefault("ACTIVE", 0L));
            stats.put("pendingScreening", merchantCounts.getOrDefault("PENDING_SCREENING", 0L));

            Map<String, Long> caseCounts = rowsToMap(caseRepository.countAllGroupByStatus());
            stats.put("openCases",
                    caseCounts.getOrDefault("NEW", 0L)
                    + caseCounts.getOrDefault("ASSIGNED", 0L)
                    + caseCounts.getOrDefault("IN_PROGRESS", 0L));
        }
        stats.put("urgentCases", 0L);

        return ResponseEntity.ok(stats);
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
        status.put("lastRun", "Today, 02:00 AM");
        status.put("status", "SUCCESS");
        status.put("merchantsProcessed", 1240);
        status.put("hitsFound", 3);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/fraud-metrics")
    public ResponseEntity<Map<String, Object>> getFraudMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("precision", "98.5%");
        metrics.put("recall", "92.1%");
        metrics.put("f1", "95.2%");
        metrics.put("falsePositives", "0.4%");
        return ResponseEntity.ok(metrics);
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
}
