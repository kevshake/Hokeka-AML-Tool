package com.posgateway.aml.service;

import com.posgateway.aml.config.FraudProperties;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fraud Detection Service
 * Performs fraud risk assessment on transactions.
 * All thresholds and rules are configurable via FraudProperties.
 */
@Service
public class FraudDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionService.class);

    /**
     * FATF / OFAC high-risk country codes (ISO 3166-1 alpha-2).
     * Compile-time fallback used when HighRiskCountryRepository is unavailable.
     */
    private static final Set<String> FATF_HIGH_RISK_COUNTRIES = Set.of(
            "KP", "IR", "MM", "SY", "YE", "SD", "LY", "SO", "CF", "SS", "VE", "AF", "IQ", "ML", "BF"
    );

    /**
     * Well-known cloud / data-centre IPv4 /8 prefixes commonly associated with
     * VPN exit nodes, hosting providers, and proxy services.  Consumer devices
     * rarely originate traffic from these prefixes, so a match is a risk signal
     * when the channel is not a server-to-server (PSP backend) call.
     *
     * Private RFC-1918 ranges (10.x, 172.16-31.x, 192.168.x) are intentionally
     * excluded — private IPs indicate the transaction arrived through a trusted
     * internal network or reverse proxy, which is not itself suspicious.
     */
    private static final Set<String> CLOUD_VPN_PREFIXES = Set.of(
            "34.", "35.", "52.", "54.", "13.", "18.", "3.",    // AWS / GCP / Azure outbound
            "104.", "130.", "142.", "146.", "147.", "149.",      // GCP, Azure, Oracle Cloud
            "185.", "194.", "195.", "198.", "199."               // common VPN/hosting ASNs
    );

    private final FraudProperties fraudProperties;
    private final TransactionRepository transactionRepository;
    private final HighRiskCountryRepository highRiskCountryRepository;

    public FraudDetectionService(FraudProperties fraudProperties,
                                 TransactionRepository transactionRepository,
                                 HighRiskCountryRepository highRiskCountryRepository) {
        this.fraudProperties = fraudProperties;
        this.transactionRepository = transactionRepository;
        this.highRiskCountryRepository = highRiskCountryRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Assess fraud risk for a {@link Transaction} (model layer).
     *
     * @param transaction The transaction to assess.
     * @return RiskAssessment containing fraud risk score and level.
     */
    public RiskAssessment assessFraudRisk(Transaction transaction) {
        if (!fraudProperties.isEnabled()) {
            logger.debug("Fraud detection disabled, returning low risk");
            return createLowRiskAssessment(transaction.getTransactionId());
        }

        logger.debug("Assessing fraud risk for transaction: {}", transaction.getTransactionId());

        int fraudScore = 0;
        List<String> riskFactors = new ArrayList<>();

        fraudScore += assessDeviceRisk(transaction, riskFactors);
        fraudScore += assessIpRisk(transaction, riskFactors);
        fraudScore += assessBehavioralRisk(transaction, riskFactors);

        if (fraudProperties.getVelocity().isCheckEnabled()) {
            fraudScore += assessVelocityRisk(transaction, riskFactors);
        }

        RiskLevel riskLevel = determineRiskLevel(fraudScore);

        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionId(transaction.getTransactionId());
        assessment.setFraudScore(fraudScore);
        assessment.setFraudRiskLevel(riskLevel);
        assessment.setRiskFactors(riskFactors);
        assessment.setAssessedAt(LocalDateTime.now());

        logger.info("Fraud risk assessment completed for transaction {}: Score={}, Level={}",
                transaction.getTransactionId(), fraudScore, riskLevel);

        return assessment;
    }

    /**
     * Assess device risk for a {@link TransactionEntity} (persistence layer).
     * Returns a score in the range [0, 100] suitable for inclusion in a composite
     * fraud score.  Used by TransactionMonitoringService to populate the
     * {@code deviceRisk} field of the monitoring DTO.
     *
     * <p>Scoring logic:
     * <ul>
     *   <li>No deviceFingerprint — 30 (missing identifier is itself a risk signal).</li>
     *   <li>Device fingerprint linked to a CRITICAL alert — 90.</li>
     *   <li>Device seen at &gt;5 distinct merchants in last 24 h — 80 (card-testing).</li>
     *   <li>Device seen at 3–5 distinct merchants in last 24 h — 50.</li>
     *   <li>Default (known device, no velocity) — 10.</li>
     * </ul>
     */
    public int assessDeviceRisk(TransactionEntity txn) {
        String fingerprint = txn.getDeviceFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            return 30;
        }

        // Check for CRITICAL (fraud) alerts linked to this device fingerprint.
        Long fraudAlerts = safeQueryLong(() ->
                transactionRepository.countFraudAlertsByDeviceFingerprint(fingerprint));
        if (fraudAlerts > 0) {
            return 90;
        }

        // Count distinct merchants this device has visited in the last 24 hours.
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Long distinctMerchants = safeQueryLong(() ->
                transactionRepository.countDistinctMerchantsByDeviceSince(fingerprint, since, LocalDateTime.now()));

        if (distinctMerchants > 5) {
            return 80;
        }
        if (distinctMerchants >= 3) {
            return 50;
        }
        return 10;
    }

    /**
     * Detect VPN / proxy / cloud-hosted IP for a {@link TransactionEntity}.
     *
     * <p>Heuristic: if the IP starts with a well-known cloud/data-centre prefix
     * (AWS, GCP, Azure, Oracle Cloud, common hosting ASNs) it is likely a VPN
     * exit node or automated bot rather than a genuine consumer device.
     *
     * @return {@code Boolean.TRUE} when VPN/proxy is suspected,
     *         {@code Boolean.FALSE} when the IP looks like a normal consumer IP,
     *         {@code null} when no IP is available on the transaction.
     */
    public Boolean detectVpn(TransactionEntity txn) {
        String ip = txn.getIpAddress();
        if (ip == null || ip.isBlank()) {
            return null;
        }

        // Private / loopback ranges are not VPN indicators.
        if (ip.startsWith("10.") || ip.startsWith("127.")
                || ip.startsWith("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return Boolean.FALSE;
        }
        if (ip.startsWith("192.168.")) {
            return Boolean.FALSE;
        }
        if (ip.startsWith("172.")) {
            // RFC-1918: 172.16.0.0/12 — only 172.16–172.31 are private.
            try {
                int secondOctet = Integer.parseInt(ip.split("\\.")[1]);
                if (secondOctet >= 16 && secondOctet <= 31) {
                    return Boolean.FALSE;
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                // Malformed IP — treat as suspicious.
                return Boolean.TRUE;
            }
        }

        for (String prefix : CLOUD_VPN_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    // =========================================================================
    // Private helpers — model.Transaction scoring
    // =========================================================================

    private int assessDeviceRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;

        String deviceFingerprint = transaction.getDeviceFingerprint();
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            score += 30;
            riskFactors.add("Missing device fingerprint");
            return score;
        }

        // Check for CRITICAL (fraud) alerts linked to this device fingerprint.
        Long fraudAlerts = safeQueryLong(() ->
                transactionRepository.countFraudAlertsByDeviceFingerprint(deviceFingerprint));
        if (fraudAlerts > 0) {
            score += 90;
            riskFactors.add("Device fingerprint associated with fraud alerts");
            return score;
        }

        // Count distinct merchants visited in the last 24 h (card-testing signal).
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Long distinctMerchants = safeQueryLong(() ->
                transactionRepository.countDistinctMerchantsByDeviceSince(
                        deviceFingerprint, since, LocalDateTime.now()));

        if (distinctMerchants > 5) {
            score += 80;
            riskFactors.add("Device used at " + distinctMerchants + " distinct merchants in last 24h");
        } else if (distinctMerchants >= 3) {
            score += 50;
            riskFactors.add("Device used at multiple merchants in last 24h");
        } else {
            score += 10;
        }

        return score;
    }

    private int assessIpRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;

        String ipAddress = transaction.getIpAddress();
        if (ipAddress == null || ipAddress.isBlank()) {
            score += 10;
            riskFactors.add("Missing IP address");
            return score;
        }

        int accumulated = 0;

        // Velocity: count transactions from this IP in the last hour.
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        Long ipTxCount = safeQueryLong(() ->
                transactionRepository.countByIpAddressSince(ipAddress, hourAgo, LocalDateTime.now()));
        if (ipTxCount > 20) {
            accumulated += 85;
            riskFactors.add("High transaction velocity from IP: " + ipTxCount + " transactions in last hour");
        }

        // High-risk country check via repository (with FATF static fallback).
        String countryCode = transaction.getCountryCode();
        if (countryCode != null && !countryCode.isBlank()) {
            boolean highRiskCountry = isHighRiskCountry(countryCode);
            if (highRiskCountry) {
                accumulated += 30;
                riskFactors.add("Transaction from high-risk country: " + countryCode);
            }
        }

        // Apply a base score of 10 for a recognised IP with no risk signals.
        if (accumulated == 0) {
            accumulated = 10;
        }

        score += Math.min(accumulated, 95);
        return score;
    }

    private int assessBehavioralRisk(Transaction transaction, List<String> riskFactors) {
        int score = 5; // floor

        long amountCents = transaction.getAmount() != null
                ? transaction.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue()
                : 0L;

        // --- Amount vs historical baseline ---
        double avgAmountCents = 0.0;
        String accountNumber = transaction.getAccountNumber();
        if (accountNumber != null && !accountNumber.isBlank()) {
            // Use average from last 30 transactions for this PAN (best proxy available).
            // The panHash is not available on model.Transaction, so we use the account
            // number column (same logical identity for this model).
            Double avg = safeQueryDouble(() ->
                    transactionRepository.avgAmountByPanInTimeWindow(
                            accountNumber,
                            LocalDateTime.now().minusDays(30),
                            LocalDateTime.now()));
            avgAmountCents = avg != null ? avg : 0.0;
        }

        if (avgAmountCents > 0 && amountCents > avgAmountCents * 3) {
            score += 60;
            riskFactors.add("Transaction amount is >3x historical average");
        }

        // --- Unusual transaction time (2:00–5:00 AM) ---
        LocalDateTime txTs = transaction.getTransactionTimestamp();
        if (txTs != null) {
            int hour = txTs.getHour();
            if (hour >= 2 && hour < 5) {
                score += 20;
                riskFactors.add("Transaction at unusual hour: " + hour + ":xx");
            }
        }

        // Cap at 95.
        return Math.min(score, 95);
    }

    private int assessVelocityRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;

        String accountNumber = transaction.getAccountNumber();
        if (accountNumber == null || accountNumber.isBlank()) {
            return score;
        }

        // Count transactions for this account in the configured velocity window.
        int windowMinutes = fraudProperties.getVelocity().getWindowMinutes();
        int maxTransactions = fraudProperties.getVelocity().getMaxTransactions();
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(windowMinutes);

        Long count = safeQueryLong(() ->
                transactionRepository.countByPanInTimeWindow(
                        accountNumber, windowStart, LocalDateTime.now()));

        if (count > maxTransactions) {
            score += 30;
            riskFactors.add("Velocity exceeded: " + count + " transactions in " + windowMinutes + " minutes");
        }

        return score;
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private boolean isHighRiskCountry(String countryCode) {
        try {
            return highRiskCountryRepository.existsByCountryCode(countryCode);
        } catch (Exception ex) {
            logger.warn("high_risk_countries lookup failed for {}: {}; using static FATF list",
                    countryCode, ex.getMessage());
            return FATF_HIGH_RISK_COUNTRIES.contains(countryCode);
        }
    }

    /**
     * Execute a repository query that returns a Long, returning 0 if the query
     * fails (DB unavailable, null result) so scoring remains non-null.
     */
    private long safeQueryLong(java.util.function.Supplier<Long> query) {
        try {
            Long result = query.get();
            return result != null ? result : 0L;
        } catch (Exception ex) {
            logger.warn("Fraud scoring query failed: {}", ex.getMessage());
            return 0L;
        }
    }

    /**
     * Execute a repository query that returns a Double, returning null on failure.
     */
    private Double safeQueryDouble(java.util.function.Supplier<Double> query) {
        try {
            return query.get();
        } catch (Exception ex) {
            logger.warn("Fraud scoring query failed: {}", ex.getMessage());
            return null;
        }
    }

    private RiskLevel determineRiskLevel(int fraudScore) {
        int threshold = fraudProperties.getScoring().getThreshold();
        int mediumThreshold = (int) (threshold * 0.7);

        if (fraudScore >= threshold) {
            return RiskLevel.HIGH;
        }
        if (fraudScore >= mediumThreshold) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private RiskAssessment createLowRiskAssessment(String transactionId) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionId(transactionId);
        assessment.setFraudScore(0);
        assessment.setFraudRiskLevel(RiskLevel.LOW);
        assessment.setAssessedAt(LocalDateTime.now());
        return assessment;
    }
}
