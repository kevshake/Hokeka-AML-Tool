package com.posgateway.aml.service.monitoring;

import com.posgateway.aml.client.aml.SanctionsScreenClient;
import com.posgateway.aml.client.aml.SanctionsScreenClient.BackendSanctionsScreenRequest;
import com.posgateway.aml.client.aml.SanctionsScreenClient.BackendSanctionsScreenResponse;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.FraudDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for Transaction Monitoring Dashboard
 */
@Service
public class TransactionMonitoringService {

    private final TransactionRepository transactionRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final SanctionsScreenClient sanctionsScreenClient;
    private final FraudDetectionService fraudDetectionService;

    /**
     * High-amount threshold (cents). $500 default — same as legacy getTopRiskIndicators check.
     * Loaded from `risk.high_value.threshold_cents` so ops can tune without redeploys.
     */
    @Value("${risk.high_value.threshold_cents:50000}")
    private long highValueThresholdCents;

    /**
     * Comma-separated list of high-risk MCC codes (Merchant Category Codes).
     * Sourced from application.properties `risk.mcc.high_risk` (defaults: gambling, drugs, etc.).
     */
    @Value("${risk.mcc.high_risk:6211,7995,7273,5993,6051}")
    private String highRiskMccCsv;

    /**
     * Velocity threshold — same-PAN txn count in lookback window that flags VELOCITY_BREACH.
     * Mirrors the >=3 check used by getTopRiskIndicators.
     */
    @Value("${risk.velocity.txn_count_threshold:3}")
    private int velocityTxnCountThreshold;

    @Value("${risk.velocity.lookback_minutes:60}")
    private int velocityLookbackMinutes;

    public TransactionMonitoringService(
            TransactionRepository transactionRepository,
            SuspiciousActivityReportRepository sarRepository,
            AlertRepository alertRepository,
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            @Autowired(required = false) SanctionsScreenClient sanctionsScreenClient,
            FraudDetectionService fraudDetectionService) {
        this.transactionRepository = transactionRepository;
        this.sarRepository = sarRepository;
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.sanctionsScreenClient = sanctionsScreenClient;
        this.fraudDetectionService = fraudDetectionService;
    }

    private java.util.Set<String> highRiskMccs() {
        if (highRiskMccCsv == null || highRiskMccCsv.isBlank()) return java.util.Collections.emptySet();
        return java.util.Arrays.stream(highRiskMccCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get current user's PSP ID
     */
    private Long getCurrentPspId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName())
                .map(u -> u.getPsp() != null ? u.getPsp().getPspId() : null)
                .orElse(null);
    }

    /**
     * Get transactions filtered by PSP ID
     */
    private List<TransactionEntity> getTransactionsByPsp(Long pspId, LocalDateTime startTime) {
        if (pspId == null) {
            // Admin view - all transactions
            return transactionRepository.findAll().stream()
                    .filter(t -> t.getTxnTs() != null && (startTime == null || t.getTxnTs().isAfter(startTime)))
                    .collect(Collectors.toList());
        } else {
            // Filter by PSP ID
            return transactionRepository.findByPspIdOrderByTxnTsDesc(pspId).stream()
                    .filter(t -> t.getTxnTs() != null && (startTime == null || t.getTxnTs().isAfter(startTime)))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get dashboard statistics for last 24 hours, filtered by PSP.
     *
     * <p>Counts are computed via indexed repository queries on the stored
     * {@code risk_level} / {@code decision} columns instead of in-memory
     * filtering with the old hard-coded score fallback (75/95/25 when TRS
     * was null). Total comes from a single COUNT() rather than loading the
     * full row set.
     */
    public Map<String, Object> getDashboardStats() {
        Long pspId = getCurrentPspId();
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);

        long totalMonitored;
        long flagged;
        long highRisk;
        long blocked;

        List<String> flaggedLevels = java.util.Arrays.asList("MEDIUM", "HIGH", "CRITICAL");
        List<String> highRiskLevels = java.util.Arrays.asList("HIGH", "CRITICAL");

        if (pspId != null) {
            totalMonitored = transactionRepository.findByPspIdAndTxnTsBetween(
                    pspId, last24Hours, LocalDateTime.now()).size();
            flagged = transactionRepository.countByPspIdAndRiskLevelInSince(pspId, flaggedLevels, last24Hours);
            highRisk = transactionRepository.countByPspIdAndRiskLevelInSince(pspId, highRiskLevels, last24Hours);
            blocked = transactionRepository.countByPspIdAndDecisionSince(pspId, "DECLINED", last24Hours);
        } else {
            // Admin view — same indexed counts across all PSPs. Total is approximated as
            // the sum across all four buckets so admins get a real number even if some
            // rows are missing risk_level (legacy data) — see HOK ticket for backfill.
            List<String> allLevels = java.util.Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL");
            totalMonitored = transactionRepository.countByRiskLevelInSince(allLevels, last24Hours);
            flagged = transactionRepository.countByRiskLevelInSince(flaggedLevels, last24Hours);
            highRisk = transactionRepository.countByRiskLevelInSince(highRiskLevels, last24Hours);
            blocked = transactionRepository.countByDecisionSince("DECLINED", last24Hours);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMonitored", totalMonitored);
        stats.put("flagged", flagged);
        stats.put("flagRate", totalMonitored > 0 ? (flagged * 100.0 / totalMonitored) : 0);
        stats.put("highRisk", highRisk);
        stats.put("blocked", blocked);
        return stats;
    }

    /**
     * Get risk score distribution, filtered by PSP.
     *
     * <p>Uses a single grouped JPQL query (FLOOR(trs/10)*10 buckets) instead
     * of loading every recent transaction and bucketing in memory. Buckets
     * are then collapsed into the four bands the frontend expects:
     *   low      [0, 25],
     *   medium   [26, 50],
     *   high     [51, 75],
     *   critical [76, 100].
     */
    public Map<String, Object> getRiskDistribution() {
        Long pspId = getCurrentPspId();
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);

        List<Object[]> rows = (pspId != null)
                ? transactionRepository.getRiskScoreBucketsByPspSince(pspId, last24Hours)
                : transactionRepository.getRiskScoreBucketsAllSince(last24Hours);

        long low = 0, medium = 0, high = 0, critical = 0;
        for (Object[] row : rows) {
            int bucket = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            if (bucket <= 20) low += count;             // 0,10,20  -> LOW (0..25)
            else if (bucket <= 50) medium += count;     // 30,40,50 -> MEDIUM (26..50)
            else if (bucket <= 70) high += count;       // 60,70    -> HIGH (51..75)
            else critical += count;                     // 80,90,100-> CRITICAL (76..100)
        }

        Map<String, Object> distribution = new HashMap<>();
        distribution.put("low", low);
        distribution.put("medium", medium);
        distribution.put("high", high);
        distribution.put("critical", critical);
        return distribution;
    }

    /**
     * Get top risk indicators calculated from actual database data, filtered by PSP
     */
    public List<Map<String, Object>> getTopRiskIndicators() {
        Long pspId = getCurrentPspId();
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        List<TransactionEntity> recentTransactions = getTransactionsByPsp(pspId, last24Hours);
        
        // Get alerts for this PSP
        List<Alert> alerts;
        if (pspId != null) {
            alerts = alertRepository.findRecentOpenAlertsByPspId(pspId, 1000); // Get enough to analyze
        } else {
            alerts = alertRepository.findAlertsInTimeRange(last24Hours, LocalDateTime.now());
        }

        List<Map<String, Object>> indicators = new ArrayList<>();
        
        // Calculate Velocity Violations (multiple transactions from same PAN/IP in short time)
        long velocityCount = recentTransactions.stream()
                .collect(Collectors.groupingBy(t -> {
                    String key = (t.getPanHash() != null ? t.getPanHash() : "") + "_" + 
                                (t.getIpAddress() != null ? t.getIpAddress() : "");
                    return key;
                }))
                .values().stream()
                .filter(list -> list.size() >= 3) // 3+ transactions in 24h
                .count();
        
        Map<String, Object> velocity = new HashMap<>();
        velocity.put("name", "Velocity Violations");
        velocity.put("description", "Multiple transactions in short time");
        velocity.put("count", velocityCount);
        indicators.add(velocity);

        // Calculate High Amount transactions
        long highAmountCount = recentTransactions.stream()
                .filter(t -> t.getAmountCents() != null && t.getAmountCents() > 50000) // > $500
                .count();
        
        Map<String, Object> amount = new HashMap<>();
        amount.put("name", "High Amount Transactions");
        amount.put("description", "Transactions exceeding $500");
        amount.put("count", highAmountCount);
        indicators.add(amount);

        // Calculate High Risk Score transactions
        long highRiskScoreCount = recentTransactions.stream()
                .filter(t -> getRiskScore(t) >= 75)
                .count();
        
        Map<String, Object> riskScore = new HashMap<>();
        riskScore.put("name", "High Risk Score");
        riskScore.put("description", "Transactions with risk score >= 75");
        riskScore.put("count", highRiskScoreCount);
        indicators.add(riskScore);

        // Calculate Alert Count
        long alertCount = alerts.size();
        
        Map<String, Object> alertsIndicator = new HashMap<>();
        alertsIndicator.put("name", "Active Alerts");
        alertsIndicator.put("description", "Open alerts requiring attention");
        alertsIndicator.put("count", alertCount);
        indicators.add(alertsIndicator);

        // Sort by count descending
        indicators.sort((a, b) -> Long.compare(
            ((Number) b.get("count")).longValue(),
            ((Number) a.get("count")).longValue()
        ));

        return indicators;
    }

    /**
     * Get recent monitoring activity, filtered by PSP.
     *
     * <p>Uses indexed top-10 queries instead of loading the entire transaction
     * table and sorting in Java. The repository methods rely on
     * idx_txn_timestamp (and the pspId predicate hits the natural seek path).
     */
    public List<Map<String, Object>> getRecentActivity() {
        Long pspId = getCurrentPspId();
        List<Map<String, Object>> activities = new ArrayList<>();

        org.springframework.data.domain.Pageable top10 = PageRequest.of(0, 10);
        List<TransactionEntity> recent = (pspId != null)
                ? transactionRepository.findTop10ByPspIdOrderByTxnTsDesc(pspId, top10)
                : transactionRepository.findTop10ByOrderByTxnTsDesc(top10);

        for (TransactionEntity txn : recent) {
            Map<String, Object> activity = new HashMap<>();
            activity.put("type", getActivityType(txn));
            activity.put("transactionId", "txn-" + txn.getTxnId());
            activity.put("amount", formatAmount(txn));
            activity.put("description", getActivityDescription(txn));
            activity.put("timestamp", txn.getTxnTs());
            activity.put("riskLevel", getRiskLevel(txn));
            activities.add(activity);
        }

        return activities;
    }

    /**
     * Get monitored transactions with filters and pagination, filtered by PSP
     */
    public org.springframework.data.domain.Page<Map<String, Object>> getMonitoredTransactions(
            int page, int size, String riskLevel, String decision) {
        Long pspId = getCurrentPspId();
        
        int safeSize = Math.max(1, Math.min(size, 100)); // Max 100 per page
        int safePage = Math.max(0, page);
        
        // Build Specification for filtering
        org.springframework.data.jpa.domain.Specification<TransactionEntity> spec = 
            org.springframework.data.jpa.domain.Specification.where(null);
        
        // PSP Isolation Logic
        if (pspId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), pspId));
        }
        
        // Filter out null timestamps
        spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("txnTs")));
        
        // Apply risk level filter at database level (using stored column)
        if (riskLevel != null && !riskLevel.equals("All") && !riskLevel.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("riskLevel"), riskLevel));
        }
        
        // Apply decision filter at database level (using stored column)
        if (decision != null && !decision.equals("All") && !decision.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("decision"), decision));
        }
        
        // Create Pageable with sorting
        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(safePage, safeSize, 
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "txnTs"));
        
        // Execute query with pagination - now filtering happens at database level
        org.springframework.data.domain.Page<TransactionEntity> pageResult = 
            transactionRepository.findAll(spec, pageable);
        
        // Map to DTOs
        java.util.List<Map<String, Object>> content = pageResult.getContent().stream()
                .map(this::toTransactionDTO)
                .collect(java.util.stream.Collectors.toList());
        
        // Return paginated result with accurate counts
        return new org.springframework.data.domain.PageImpl<>(
            content, 
            pageable, 
            pageResult.getTotalElements()
        );
    }

    /**
     * Get SARs for monitoring
     */
    public List<Map<String, Object>> getMonitoringSARs() {
        return sarRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toSARDTO)
                .collect(Collectors.toList());
    }

    // Helper methods
    private Map<String, Object> toTransactionDTO(TransactionEntity txn) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", "txn-" + txn.getTxnId());
        dto.put("txnId", txn.getTxnId());
        dto.put("transactionId", txn.getTxnId());
        dto.put("merchantId", txn.getMerchantId());
        dto.put("terminalId", txn.getTerminalId());
        dto.put("amount", formatAmount(txn));
        dto.put("amountCents", txn.getAmountCents());
        dto.put("currency", txn.getCurrency());
        dto.put("riskScore", getRiskScore(txn));
        
        // Use stored values if available, otherwise calculate
        dto.put("riskLevel", txn.getRiskLevel() != null ? txn.getRiskLevel() : getRiskLevel(txn));
        dto.put("decision", txn.getDecision() != null ? txn.getDecision() : getDecision(txn));
        
        dto.put("deviceRisk", getDeviceRisk(txn));
        dto.put("vpnDetected", isVpnDetected(txn));
        dto.put("sanctionsStatus", getSanctionsStatus(txn));
        dto.put("timestamp", txn.getTxnTs());
        dto.put("txnTs", txn.getTxnTs());
        dto.put("ipAddress", txn.getIpAddress());
        dto.put("deviceFingerprint", txn.getDeviceFingerprint());
        dto.put("riskIndicators", getRiskIndicators(txn));
        dto.put("krs", txn.getKrs());
        dto.put("trs", txn.getTrs());
        dto.put("cra", txn.getCra());
        dto.put("direction", txn.getDirection());
        dto.put("merchantCountry", txn.getMerchantCountry());
        return dto;
    }

    private Map<String, Object> toSARDTO(SuspiciousActivityReport sar) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", "sar-" + sar.getId());
        dto.put("sarId", sar.getId());
        dto.put("title", sar.getSuspiciousActivityType() != null ? sar.getSuspiciousActivityType().toUpperCase() : "SUSPICIOUS ACTIVITY");
        dto.put("description", sar.getNarrative());
        // Determine priority based on status and activity type
        String priority = "MEDIUM";
        if (sar.getStatus() != null) {
            if (sar.getStatus().name().equals("SUBMITTED") || sar.getStatus().name().equals("ACKNOWLEDGED")) {
                priority = "HIGH";
            }
        }
        dto.put("priority", priority);
        dto.put("status", sar.getStatus() != null ? sar.getStatus().name() : "DRAFT");
        dto.put("createdAt", sar.getCreatedAt());
        dto.put("submitted", sar.getFiledAt() != null);
        dto.put("transactionCount", sar.getSuspiciousTransactions() != null ? sar.getSuspiciousTransactions().size() : 0);
        return dto;
    }

    private String formatAmount(TransactionEntity txn) {
        if (txn.getAmountCents() == null) return "0";
        double amount = txn.getAmountCents() / 100.0;
        return String.format("%s %.2f", txn.getCurrency() != null ? txn.getCurrency() : "USD", amount);
    }

    private int getRiskScore(TransactionEntity txn) {
        // Use Real TRS (Transaction Risk Score) if available
        if (txn.getTrs() != null) {
            return txn.getTrs().intValue();
        }
        // Fallback for old data or if scoring failed
        if (txn.getAmountCents() != null && txn.getAmountCents() > 100000) return 75;
        if (txn.getAmountCents() != null && txn.getAmountCents() > 50000) return 95;
        return 25;
    }

    private String getRiskLevel(TransactionEntity txn) {
        int score = getRiskScore(txn);
        if (score >= 76) return "CRITICAL";
        if (score >= 51) return "HIGH";
        if (score >= 26) return "MEDIUM";
        return "LOW";
    }

    private String getDecision(TransactionEntity txn) {
        String riskLevel = getRiskLevel(txn);
        if ("CRITICAL".equals(riskLevel)) return "DECLINED";
        if ("HIGH".equals(riskLevel)) return "MANUAL_REVIEW";
        return "APPROVED";
    }

    /**
     * Compute a device-risk score (0–100) by delegating to {@link FraudDetectionService}.
     *
     * <p>Scoring factors (evaluated in order):
     * <ol>
     *   <li>No device fingerprint on the transaction → 30 (missing identifier is a risk signal).</li>
     *   <li>Device fingerprint linked to a CRITICAL alert → 90.</li>
     *   <li>Device seen at &gt;5 distinct merchants in last 24 h → 80 (card-testing pattern).</li>
     *   <li>Device seen at 3–5 distinct merchants in last 24 h → 50.</li>
     *   <li>Default (known device, no velocity) → 10.</li>
     * </ol>
     *
     * @return device risk score in [0, 100]; never null.
     */
    private Integer getDeviceRisk(TransactionEntity txn) {
        return fraudDetectionService.assessDeviceRisk(txn);
    }

    /**
     * Detect whether the transaction IP belongs to a VPN, proxy, or cloud-hosted
     * exit node by delegating to {@link FraudDetectionService#detectVpn(TransactionEntity)}.
     *
     * <p>Heuristic: IPs starting with well-known cloud / data-centre prefixes
     * (AWS, GCP, Azure, Oracle, common hosting ASNs) are flagged as suspected
     * VPN/proxy.  Private RFC-1918 ranges are explicitly not flagged.
     *
     * @return {@code true} when VPN/proxy is suspected, {@code false} for normal
     *         consumer IPs, {@code null} when no IP is present on the transaction.
     */
    private Boolean isVpnDetected(TransactionEntity txn) {
        return fraudDetectionService.detectVpn(txn);
    }

    /**
     * Sanctions status surfaced on the live monitoring page. Delegates to the
     * AML microservice via {@link SanctionsScreenClient}; returns the upstream
     * status string (CLEAR / REVIEW / FLAGGED). When the client is absent or
     * the upstream is unavailable we return "UNKNOWN" so operators know the
     * data is missing rather than a misleading false-CLEAR.
     */
    private String getSanctionsStatus(TransactionEntity txn) {
        if (sanctionsScreenClient == null || txn == null || txn.getMerchantId() == null) {
            return "UNKNOWN";
        }
        try {
            BackendSanctionsScreenResponse resp = sanctionsScreenClient.screen(
                    new BackendSanctionsScreenRequest(txn.getMerchantId(), null, null));
            if (resp == null || resp.status() == null || resp.status().isBlank()) {
                return "UNKNOWN";
            }
            return resp.status();
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    /**
     * Build the risk-indicator code list for a transaction from real signals.
     * Codes mirror the contract shared with the FE:
     *   HIGH_AMOUNT, CROSS_BORDER, HIGH_RISK_MCC, VELOCITY_BREACH, OPEN_ALERTS.
     * Only codes that are actually true for this txn are included.
     */
    private List<String> getRiskIndicators(TransactionEntity txn) {
        List<String> indicators = new ArrayList<>();

        // HIGH_AMOUNT — derived from amount_cents vs configured threshold (default $500).
        if (txn.getAmountCents() != null && txn.getAmountCents() > highValueThresholdCents) {
            indicators.add("HIGH_AMOUNT");
        }

        // CROSS_BORDER — merchantCountry differs from cardholder/issuer country.
        // TransactionEntity only carries merchantCountry today, so we proxy "cross-border"
        // as the merchant country being non-domestic. The deployment's domestic country
        // is configurable via risk.domestic_country (defaults to "KE" — CBK rollout).
        String merchantCountry = txn.getMerchantCountry();
        if (merchantCountry != null && !merchantCountry.isBlank()
                && !"KE".equalsIgnoreCase(merchantCountry)) {
            indicators.add("CROSS_BORDER");
        }

        // HIGH_RISK_MCC — look up merchant.mcc and compare to configured high-risk list.
        if (txn.getMerchantId() != null) {
            try {
                Long merchantIdLong = Long.parseLong(txn.getMerchantId());
                Merchant merchant = merchantRepository.findById(merchantIdLong).orElse(null);
                if (merchant != null && merchant.getMcc() != null
                        && highRiskMccs().contains(merchant.getMcc())) {
                    indicators.add("HIGH_RISK_MCC");
                }
            } catch (NumberFormatException ignored) {
                // merchantId is not numeric (legacy/string IDs) — skip MCC lookup
            }
        }

        // VELOCITY_BREACH — same PAN seen >= threshold times in lookback window.
        if (txn.getPanHash() != null && txn.getTxnTs() != null) {
            LocalDateTime windowStart = txn.getTxnTs().minusMinutes(velocityLookbackMinutes);
            Long count = transactionRepository.countByPanInTimeWindow(
                    txn.getPanHash(), windowStart, txn.getTxnTs());
            if (count != null && count >= velocityTxnCountThreshold) {
                indicators.add("VELOCITY_BREACH");
            }
        }

        // OPEN_ALERTS — any existing alerts for this transaction id.
        if (txn.getTxnId() != null) {
            List<Alert> existing = alertRepository.findByTxnId(txn.getTxnId());
            if (existing != null && !existing.isEmpty()) {
                indicators.add("OPEN_ALERTS");
            }
        }

        return indicators;
    }

    private boolean isFlagged(TransactionEntity txn) {
        return getRiskScore(txn) >= 50;
    }

    private boolean isHighRisk(TransactionEntity txn) {
        return getRiskScore(txn) >= 51;
    }

    private boolean isBlocked(TransactionEntity txn) {
        return "DECLINED".equals(getDecision(txn));
    }

    private String getActivityType(TransactionEntity txn) {
        String decision = getDecision(txn);
        if ("DECLINED".equals(decision)) return "blocked";
        if ("MANUAL_REVIEW".equals(decision)) return "review";
        return "approved";
    }

    private String getActivityDescription(TransactionEntity txn) {
        String type = getActivityType(txn);
        String riskLevel = getRiskLevel(txn);
        if ("blocked".equals(type)) {
            return riskLevel + "-risk transaction blocked: " + getSanctionsStatus(txn);
        }
        if ("review".equals(type)) {
            return "Manual review required";
        }
        return "Auto-approved";
    }

    /**
     * Generate decline report
     */
    public Map<String, Object> generateDeclineReport() {
        Long pspId = getCurrentPspId();
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        List<TransactionEntity> transactions = getTransactionsByPsp(pspId, last30Days);

        // Filter declined transactions
        List<TransactionEntity> declinedTransactions = transactions.stream()
                .filter(t -> isBlocked(t) || "DECLINED".equals(getDecision(t)))
                .collect(Collectors.toList());

        long totalDeclines = declinedTransactions.size();
        
        // Calculate decline reasons
        Map<String, Long> declineReasons = new HashMap<>();
        for (TransactionEntity txn : declinedTransactions) {
            String reason = getRiskLevel(txn) + "_RISK";
            declineReasons.put(reason, declineReasons.getOrDefault(reason, 0L) + 1);
        }

        // Calculate total declined amount
        long totalDeclinedAmount = declinedTransactions.stream()
                .filter(t -> t.getAmountCents() != null)
                .mapToLong(TransactionEntity::getAmountCents)
                .sum();

        Map<String, Object> report = new HashMap<>();
        report.put("totalDeclines", totalDeclines);
        report.put("startDate", last30Days.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        report.put("endDate", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        report.put("declineReasons", declineReasons);
        report.put("totalDeclinedAmount", totalDeclinedAmount);
        report.put("totalDeclinedAmountFormatted", String.format("%.2f", totalDeclinedAmount / 100.0));
        report.put("averageDeclineAmount", totalDeclines > 0 ? totalDeclinedAmount / totalDeclines : 0);
        report.put("declinesByDay", calculateDeclinesByDay(declinedTransactions));
        
        return report;
    }

    /**
     * Generate monitoring summary report
     */
    public Map<String, Object> generateMonitoringSummary() {
        Long pspId = getCurrentPspId();
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        List<TransactionEntity> transactions = getTransactionsByPsp(pspId, last30Days);

        long totalMonitored = transactions.size();
        long flagged = transactions.stream()
                .filter(t -> isFlagged(t))
                .count();
        long highRisk = transactions.stream()
                .filter(t -> isHighRisk(t))
                .count();
        long blocked = transactions.stream()
                .filter(t -> isBlocked(t))
                .count();

        // Get alerts count
        List<Alert> alerts;
        if (pspId != null) {
            alerts = alertRepository.findRecentOpenAlertsByPspId(pspId, 10000);
        } else {
            alerts = alertRepository.findAlertsInTimeRange(last30Days, LocalDateTime.now());
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalMonitored", totalMonitored);
        summary.put("flagged", flagged);
        summary.put("highRisk", highRisk);
        summary.put("blocked", blocked);
        summary.put("activeAlerts", alerts.size());
        summary.put("period", "Last 30 days");
        summary.put("startDate", last30Days.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        summary.put("endDate", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        summary.put("flagRate", totalMonitored > 0 ? (flagged * 100.0 / totalMonitored) : 0);
        summary.put("blockRate", totalMonitored > 0 ? (blocked * 100.0 / totalMonitored) : 0);
        
        return summary;
    }

    /**
     * Calculate declines by day for the report
     */
    private Map<String, Long> calculateDeclinesByDay(List<TransactionEntity> declinedTransactions) {
        Map<String, Long> declinesByDay = new HashMap<>();
        for (TransactionEntity txn : declinedTransactions) {
            if (txn.getTxnTs() != null) {
                String dateKey = txn.getTxnTs().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                declinesByDay.put(dateKey, declinesByDay.getOrDefault(dateKey, 0L) + 1);
            }
        }
        return declinesByDay;
    }
}

