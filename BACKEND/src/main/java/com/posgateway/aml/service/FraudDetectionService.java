package com.posgateway.aml.service;

import com.posgateway.aml.config.FraudProperties;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.limits.VelocityRule;
import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.limits.VelocityRuleRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.service.cache.FeatureCacheService;
import com.posgateway.aml.service.enrichment.IpGeoService;
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

    // Score weights (integer points added to fraudScore, max ~100)
    private static final int SCORE_DEVICE_MISSING     = 10;
    private static final int SCORE_DEVICE_BLACKLISTED = 40;
    private static final int SCORE_DEVICE_NEW         = 15;
    private static final int SCORE_IP_MISSING         = 10;
    private static final int SCORE_IP_BLACKLISTED     = 30;
    private static final int SCORE_COUNTRY_HIGH       = 20;
    private static final int SCORE_COUNTRY_CRITICAL   = 35;
    private static final int SCORE_GEO_HOPPING        = 15;  // >3 unique countries in 24h
    private static final int SCORE_VELOCITY_LOW       = 10;
    private static final int SCORE_VELOCITY_MEDIUM    = 20;
    private static final int SCORE_VELOCITY_HIGH      = 30;
    private static final int SCORE_VELOCITY_CRITICAL  = 45;

    // Device counter TTL: 7 days (matching FeatureCacheService TTL_FEATURES_DAYS)
    private static final long DEVICE_COUNTER_TTL_S = 7 * 24 * 3600L;

    private final FraudProperties fraudProperties;
    private final TransactionRepository transactionRepository;
    private final FeatureCacheService featureCacheService;
    private final HighRiskCountryRepository highRiskCountryRepository;
    private final VelocityRuleRepository velocityRuleRepository;
    private final IpGeoService ipGeoService;
    private final com.posgateway.aml.client.aml.AmlMicroserviceClient amlMicroserviceClient;

    public FraudDetectionService(FraudProperties fraudProperties,
                                 TransactionRepository transactionRepository,
                                 FeatureCacheService featureCacheService,
                                 HighRiskCountryRepository highRiskCountryRepository,
                                 VelocityRuleRepository velocityRuleRepository,
                                 IpGeoService ipGeoService,
                                 com.posgateway.aml.client.aml.AmlMicroserviceClient amlMicroserviceClient) {
        this.fraudProperties = fraudProperties;
        this.transactionRepository = transactionRepository;
        this.featureCacheService = featureCacheService;
        this.highRiskCountryRepository = highRiskCountryRepository;
        this.velocityRuleRepository = velocityRuleRepository;
        this.ipGeoService = ipGeoService;
        this.amlMicroserviceClient = amlMicroserviceClient;
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

        logger.info("Fraud assessment txn={} score={} level={}",
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

    // ──────────────────────────────────────────────────────────────────────────
    // Device risk
    // ──────────────────────────────────────────────────────────────────────────

    private int assessDeviceRisk(Transaction transaction, List<String> riskFactors) {
        String fingerprint = transaction.getDeviceFingerprint();

        if (fingerprint == null || fingerprint.isBlank()) {
            riskFactors.add("Missing device fingerprint");
            return SCORE_DEVICE_MISSING;
        }

        int score = 0;
        String customerId = transaction.getAccountNumber();

        try {
            // Hard-stop: device is on the explicit blacklist
            if (featureCacheService.isBlacklisted("device", fingerprint)) {
                riskFactors.add("Blacklisted device fingerprint: " + fingerprint);
                return SCORE_DEVICE_BLACKLISTED; // nothing else needed
            }

            // New device for this customer (counter == 0 means never seen before)
            String deviceCounterKey = "device:" + fingerprint;
            long seenCount = featureCacheService.getCounter(customerId, deviceCounterKey);
            if (seenCount == 0) {
                score += SCORE_DEVICE_NEW;
                riskFactors.add("New device fingerprint for this customer");
            }
            featureCacheService.incrementCounter(customerId, deviceCounterKey, DEVICE_COUNTER_TTL_S);

            // P3-C shadow write to Aerospike via aml-microservice (fire-and-forget).
            // Redis remains the read-side source of truth; once aml-microservice
            // exposes /internal/v1/cache/device, the rule engine inside that
            // service can read from Aerospike directly with sub-ms latency.
            try {
                java.util.Map<String, Object> obs = new java.util.HashMap<>();
                obs.put("customerId", customerId);
                obs.put("seenCount", seenCount + 1);
                obs.put("lastSeenMs", System.currentTimeMillis());
                amlMicroserviceClient.recordDeviceObservation(fingerprint, obs);
            } catch (Exception ignore) { /* logged by client */ }

        } catch (Exception e) {
            logger.warn("Device risk check failed for txn={}: {}", transaction.getTransactionId(), e.getMessage());
        }

        return score;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IP / geo risk
    // ──────────────────────────────────────────────────────────────────────────

    private int assessIpRisk(Transaction transaction, List<String> riskFactors) {
        String ip = transaction.getIpAddress();

        if (ip == null || ip.isBlank()) {
            riskFactors.add("Missing IP address");
            return SCORE_IP_MISSING;
        }

        int score = 0;
        String customerId = transaction.getAccountNumber();

        try {
            // Hard-stop: IP is on the explicit blacklist
            if (featureCacheService.isBlacklisted("ip", ip)) {
                score += SCORE_IP_BLACKLISTED;
                riskFactors.add("Blacklisted IP address: " + ip);
            }

            // Resolve country — prefer the field already on the transaction (set by enrichment
            // pipeline), fall back to real-time GeoIP lookup
            String countryCode = transaction.getCountryCode();
            if (countryCode == null || countryCode.isBlank()) {
                countryCode = ipGeoService.lookupCountry(ip).orElse(null);
            }

            if (countryCode != null) {
                // High-risk / critical country check (reads from high_risk_countries table)
                var hrcOpt = highRiskCountryRepository.findByCountryCode(countryCode);
                if (hrcOpt.isPresent()) {
                    String level = hrcOpt.get().getRiskLevel();
                    if ("CRITICAL".equals(level)) {
                        score += SCORE_COUNTRY_CRITICAL;
                        riskFactors.add("Transaction from CRITICAL risk country: " + countryCode);
                    } else {
                        score += SCORE_COUNTRY_HIGH;
                        riskFactors.add("Transaction from HIGH risk country: " + countryCode);
                    }
                }

                // Track distinct countries per customer (24h window) and flag geo-hopping
                featureCacheService.addCountry(customerId, countryCode, 24);
                int uniqueCountries = featureCacheService.getUniqueCountryCount(customerId, 24);
                if (uniqueCountries > 3) {
                    score += SCORE_GEO_HOPPING;
                    riskFactors.add("Geo-hopping detected: " + uniqueCountries + " countries in 24h");
                }
            }

            // P3-C shadow write of IP observation to Aerospike (fire-and-forget)
            try {
                java.util.Map<String, Object> obs = new java.util.HashMap<>();
                obs.put("customerId", customerId);
                obs.put("country", countryCode);
                obs.put("lastSeenMs", System.currentTimeMillis());
                amlMicroserviceClient.recordIpObservation(ip, obs);
            } catch (Exception ignore) { /* logged by client */ }

        } catch (Exception e) {
            logger.warn("IP risk check failed for txn={}: {}", transaction.getTransactionId(), e.getMessage());
        }

        return score;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Behavioral risk (time, MCC patterns — future expansion point)
    // ──────────────────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────────────────
    // Velocity risk — evaluated against active rules from velocity_rules table
    // ──────────────────────────────────────────────────────────────────────────

    private int assessVelocityRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        String customerId = transaction.getAccountNumber();
        long nowMs = System.currentTimeMillis();

        // Record this transaction in the sliding window before evaluating so rules
        // include the current txn in their counts
        featureCacheService.recordTransaction(customerId, nowMs, nowMs);

        // P3-B shadow write to Aerospike velocity counter (fire-and-forget)
        try {
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("tsMs", nowMs);
            if (transaction.getAmount() != null) {
                event.put("amountCents", transaction.getAmount().movePointRight(2).longValueExact());
            }
            event.put("country", transaction.getCountryCode());
            amlMicroserviceClient.recordVelocityEvent(customerId, event);
        } catch (Exception ignore) { /* logged by client */ }

        try {
            List<VelocityRule> activeRules = velocityRuleRepository.findByStatus("ACTIVE");

            for (VelocityRule rule : activeRules) {
                // PSP-scoped rules apply only to their PSP; global rules (pspId null) apply to all
                if (rule.getPspId() != null && !rule.getPspId().equals(transaction.getPspId())) {
                    continue;
                }

                long windowMs = (long) rule.getTimeWindowMinutes() * 60_000L;
                long txCount = featureCacheService.getTxCountInWindow(customerId, windowMs);

                if (txCount > rule.getMaxTransactions()) {
                    score += velocityScoreForLevel(rule.getRiskLevel());
                    riskFactors.add(String.format(
                            "Velocity rule '%s' breached: %d txns in %d min (limit %d)",
                            rule.getRuleName(), txCount, rule.getTimeWindowMinutes(), rule.getMaxTransactions()));
                }
            }

        } catch (Exception e) {
            logger.warn("Velocity risk check failed for txn={}: {}", transaction.getTransactionId(), e.getMessage());
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

    private int velocityScoreForLevel(String riskLevel) {
        if (riskLevel == null) return SCORE_VELOCITY_LOW;
        return switch (riskLevel.toUpperCase()) {
            case "CRITICAL" -> SCORE_VELOCITY_CRITICAL;
            case "HIGH"     -> SCORE_VELOCITY_HIGH;
            case "MEDIUM"   -> SCORE_VELOCITY_MEDIUM;
            default         -> SCORE_VELOCITY_LOW;
        };
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
        if (fraudScore >= threshold)       return RiskLevel.HIGH;
        if (fraudScore >= mediumThreshold) return RiskLevel.MEDIUM;
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
