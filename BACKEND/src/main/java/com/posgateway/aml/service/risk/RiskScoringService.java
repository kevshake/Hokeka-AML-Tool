package com.posgateway.aml.service.risk;

import com.posgateway.aml.client.aml.AmlMicroserviceClient;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantRiskScore;
import com.posgateway.aml.entity.risk.CountryRiskScore;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.merchant.MerchantRiskScoreRepository;
import com.posgateway.aml.repository.risk.CountryRiskRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.service.rules.RulesExecutionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Risk scoring (KRS, TRS, CRA).
 *
 * <p>All risk reference data — country risk, FATF list, alert history,
 * transaction volume — is read from the database. No hard-coded country
 * lists. The MCC risk fallback is small and acts as a default when the
 * MCC has no entry in the future {@code mcc_risk} reference table; once
 * that table is added, swap {@link #scoreMccRisk(String)} to read from it.
 */
@Service
public class RiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringService.class);

    // W19-3 fix: all weights below were hardcoded static final constants -- externalized via
    // @Value so compliance can tune the risk model without a redeploy, same pattern already used
    // elsewhere in the codebase (e.g. AmlCheckService's risk-country lists, MultiAssetRiskEngine's
    // travel-rule threshold). Defaults are the exact prior hardcoded values, so behavior is
    // unchanged unless the properties are explicitly set.

    // ── Composite KRS weights ────────────────────────────────────────────────
    @Value("${risk.weights.krs.country-residence:0.5}")
    private double wCountryResidence;
    @Value("${risk.weights.krs.nationality:0.3}")
    private double wNationality;
    @Value("${risk.weights.krs.age:0.2}")
    private double wAge;

    // ── Composite TRS weights ────────────────────────────────────────────────
    @Value("${risk.weights.trs.origin-country:0.3}")
    private double wOriginCountry;
    @Value("${risk.weights.trs.dest-country:0.3}")
    private double wDestCountry;
    @Value("${risk.weights.trs.amount:0.4}")
    private double wAmount;

    // ── CRA component weights (sum = 1.0) ────────────────────────────────────
    @Value("${risk.weights.cra.country:0.25}")
    private double craWCountry;
    @Value("${risk.weights.cra.pep-sanctions:0.30}")
    private double craWPepSanc;
    @Value("${risk.weights.cra.industry:0.15}")
    private double craWIndustry;
    @Value("${risk.weights.cra.volume:0.15}")
    private double craWVolume;
    @Value("${risk.weights.cra.alerts:0.10}")
    private double craWAlerts;
    @Value("${risk.weights.cra.age:0.05}")
    private double craWAge;

    // ── CRA history windows ──────────────────────────────────────────────────
    @Value("${risk.weights.cra.volume-lookback-days:30}")
    private int volumeLookbackDays;
    @Value("${risk.weights.cra.alert-lookback-days:90}")
    private int alertLookbackDays;

    // ── Industry risk fallback (until mcc_risk table exists) ─────────────────
        private final com.posgateway.aml.service.risk.MccRiskConfig mccRiskConfig;

        private static final double NEUTRAL_RISK = 50.0;

    // ── Dependencies ─────────────────────────────────────────────────────────
    private final MeterRegistry meterRegistry;
    private final RulesExecutionService rulesExecutionService;
    private final CountryRiskRepository countryRiskRepository;
    private final MerchantRepository merchantRepository;
    private final HighRiskCountryRepository highRiskCountryRepository;
    private final BeneficialOwnerRepository beneficialOwnerRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final MerchantRiskScoreRepository merchantRiskScoreRepository;
    private final AmlMicroserviceClient amlMicroserviceClient;

    public RiskScoringService(MeterRegistry meterRegistry,
                                  RulesExecutionService rulesExecutionService,
                                  CountryRiskRepository countryRiskRepository,
                                  MerchantRepository merchantRepository,
                                  HighRiskCountryRepository highRiskCountryRepository,
                                  BeneficialOwnerRepository beneficialOwnerRepository,
                                  TransactionRepository transactionRepository,
                                  AlertRepository alertRepository,
                                  MerchantRiskScoreRepository merchantRiskScoreRepository,
                                  AmlMicroserviceClient amlMicroserviceClient,
                                  MccRiskConfig mccRiskConfig) {
        this.meterRegistry = meterRegistry;
        this.rulesExecutionService = rulesExecutionService;
        this.countryRiskRepository = countryRiskRepository;
        this.merchantRepository = merchantRepository;
        this.highRiskCountryRepository = highRiskCountryRepository;
        this.beneficialOwnerRepository = beneficialOwnerRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.merchantRiskScoreRepository = merchantRiskScoreRepository;
        this.amlMicroserviceClient = amlMicroserviceClient;
                this.mccRiskConfig = mccRiskConfig;
            }

    // ─────────────────────────────────────────────────────────────────────────
    // KRS — KYC Risk Score (per merchant)
    // ─────────────────────────────────────────────────────────────────────────

    public Double calculateKrs(Merchant merchant) {
        if (merchant == null) return NEUTRAL_RISK;

        double scoreCountry = getCountryRisk(merchant.getCountry());
        double scoreMcc     = scoreMccRisk(merchant.getMcc());
        double scoreAge     = merchant.isNew() ? 60.0 : 20.0;

        double weightedSum = scoreCountry * wCountryResidence
                           + scoreMcc     * wNationality
                           + scoreAge     * wAge;
        double sumWeights  = wCountryResidence + wNationality + wAge;
        double krs = weightedSum / sumWeights;

        meterRegistry.gauge("aml.risk.krs",
                Tags.of("merchant_id", String.valueOf(merchant.getMerchantId())), krs);
        return krs;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRS — Transaction Risk Score
    // ─────────────────────────────────────────────────────────────────────────

    public Double calculateTrs(String originCountry, String destCountry, BigDecimal amount) {
        double scoreOrigin = getCountryRisk(originCountry);
        double scoreDest   = getCountryRisk(destCountry);
        double scoreAmount = calculateAmountRisk(amount);

        double weightedSum = scoreOrigin * wOriginCountry
                           + scoreDest   * wDestCountry
                           + scoreAmount * wAmount;
        double sumWeights  = wOriginCountry + wDestCountry + wAmount;
        double trs = weightedSum / sumWeights;

        meterRegistry.gauge("aml.risk.trs",
                Tags.of("origin", nullSafe(originCountry), "dest", nullSafe(destCountry)), trs);
        return trs;
    }

    /** Rolling-average update of CRA from a new TRS observation. */
    public Double updateCra(Double currentCra, Double newTrs) {
        if (currentCra == null || currentCra == 0.0) return newTrs;
        double newCra = (currentCra + newTrs) / 2.0;
        meterRegistry.gauge("aml.risk.cra", Tags.of("type", "rolling_avg"), newCra);
        return newCra;
    }

    public Map<String, Object> calculateOverallRisk(String txnId, Map<String, Object> features, Map<String, Object> riskDetails) {
        // 1. Calculate KRS from the persisted merchant profile when merchant_id is present.
        double krsScore = resolveMerchant(features)
                .map(this::calculateKrs)
                .orElseGet(() -> ((Number) features.getOrDefault("krs_score", 50.0)).doubleValue());

        double baseTrs = calculateTrs(
                (String) features.getOrDefault("origin_country", null),
                (String) features.getOrDefault("destination_country", null),
                new BigDecimal(features.getOrDefault("amount", 0.0).toString()));

        RuleEvaluationResult ruleResult = rulesExecutionService.evaluateRules(
                Long.valueOf(txnId), features,
                ((Number) riskDetails.getOrDefault("mlScore", 0.0)).doubleValue());

        double trsScore = calculateTrs(features, ruleResult);
        double craScore = calculateCra(features);
        double mlScore  = ((Number) riskDetails.getOrDefault("mlScore", 0.0)).doubleValue();

        double finalScore = krsScore * 0.3 + trsScore * 0.4 + craScore * 0.3;
        if ("BLOCK".equals(ruleResult.getDecision())) finalScore = 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("finalScore",   finalScore);
        result.put("krsScore",     krsScore);
        result.put("trsScore",     trsScore);
        result.put("craScore",     craScore);
        result.put("mlScore",      mlScore);
        result.put("baseTrs",      baseTrs);
        result.put("ruleDecision", ruleResult.getDecision());
        result.put("ruleReasons",  ruleResult.getReasons());
        return result;
    }

    private java.util.Optional<Merchant> resolveMerchant(Map<String, Object> features) {
        Object rawMerchantId = features.get("merchant_id");
        if (rawMerchantId == null) {
            rawMerchantId = features.get("merchantId");
        }
        if (rawMerchantId == null) {
            return java.util.Optional.empty();
        }
        try {
            Long merchantId = Long.valueOf(rawMerchantId.toString());
            return merchantRepository.findByMerchantId(merchantId);
        } catch (NumberFormatException ex) {
            log.warn("Cannot resolve merchant KRS for non-numeric merchant_id={}", rawMerchantId);
            return java.util.Optional.empty();
        }
    }

    /**
     * Compute CRA from real data: country risk, PEP/sanctions screening,
     * industry MCC, recent transaction volume vs expected, recent alerts,
     * and business age. Persists the result to {@code merchants.cra} and
     * appends an audit row to {@code merchant_risk_scores}.
     *
     * <p>If {@code features} contains no resolvable {@code merchant_id}
     * the calculation degrades to feature-only inputs (country + amount)
     * and is NOT persisted.
     */
    @Transactional
    public double calculateCra(Map<String, Object> features) {
        Long merchantId = parseLong(features.get("merchant_id"));

        if (merchantId == null) {
            return calculateCraFeatureOnly(features);
        }

        Optional<Merchant> merchantOpt = merchantRepository.findById(merchantId);
        if (merchantOpt.isEmpty()) {
            log.warn("CRA: merchant {} not found, falling back to feature-only", merchantId);
            return calculateCraFeatureOnly(features);
        }

        Merchant merchant = merchantOpt.get();

        int countryScore  = (int) Math.round(getCountryRisk(merchant.getCountry()));
        int pepSancScore  = (int) Math.round(scorePepAndSanctions(merchant));
        int industryScore = (int) Math.round(scoreMccRisk(merchant.getMcc()));
        int volumeScore   = (int) Math.round(scoreVolume(merchant));
        int alertScore    = (int) Math.round(scoreAlertHistory(merchantId));
        int ageScore      = (int) Math.round(scoreBusinessAge(merchant.getRegistrationDate()));

        double cra = countryScore  * craWCountry
                   + pepSancScore  * craWPepSanc
                   + industryScore * craWIndustry
                   + volumeScore   * craWVolume
                   + alertScore    * craWAlerts
                   + ageScore      * craWAge;

        cra = Math.max(0.0, Math.min(100.0, cra));

        String level = riskLevelFor(cra);
        persistCra(merchant, cra, level,
                countryScore, pepSancScore, industryScore, volumeScore, alertScore, ageScore);

        meterRegistry.gauge("aml.risk.cra",
                Tags.of("merchant_id", String.valueOf(merchantId), "level", level), cra);
        log.info("CRA merchant={} score={} level={} (country={}, pep_sanc={}, industry={}, volume={}, alerts={}, age={})",
                merchantId, String.format("%.1f", cra), level,
                countryScore, pepSancScore, industryScore, volumeScore, alertScore, ageScore);

        return cra;
    }

    private double calculateCraFeatureOnly(Map<String, Object> features) {
        String country = (String) features.getOrDefault("country_code", null);
        BigDecimal amount = parseAmount(features.get("amount"));
        double cra = getCountryRisk(country) * 0.6 + calculateAmountRisk(amount) * 0.4;
        meterRegistry.gauge("aml.risk.cra", Tags.of("source", "feature_only"), cra);
        return cra;
    }

    private void persistCra(Merchant merchant, double cra, String level,
                            int countryScore, int pepSancScore, int industryScore,
                            int volumeScore, int alertScore, int ageScore) {
        try {
            merchant.setCra(cra / 100.0); // merchants.cra column stored as 0.0–1.0
            merchant.setRiskLevel(level);
            merchantRepository.save(merchant);

            MerchantRiskScore audit = new MerchantRiskScore();
            audit.setMerchant(merchant);
            audit.setTotalScore((int) Math.round(cra));
            audit.setRiskLevel(level);
            audit.setCountryRiskScore(countryScore);
            audit.setSanctionsScore(pepSancScore);
            audit.setPepScore(pepSancScore);
            audit.setIndustryRiskScore(industryScore);
            audit.setVolumeRiskScore(volumeScore);
            audit.setBusinessAgeScore(ageScore);
            audit.setDecision(decisionFor(level));
            audit.setDecisionReason(String.format(
                    "country=%d pep_sanc=%d industry=%d volume=%d alerts=%d age=%d",
                    countryScore, pepSancScore, industryScore, volumeScore, alertScore, ageScore));
            audit.setCalculatedAt(LocalDateTime.now());
            audit.setCalculatedBy("RiskScoringService");
            audit.setRulesVersion("CRA-v1");
            merchantRiskScoreRepository.save(audit);

            // P3-A: push the freshly-computed profile to Aerospike via the
            // aml-microservice so hot-path rule evaluation reads sub-millisecond
            // instead of round-tripping to Postgres. Best-effort — Postgres is
            // the source of truth, the cache is purely a performance layer.
            java.util.Map<String, Object> profile = new java.util.HashMap<>();
            profile.put("merchantId", merchant.getMerchantId());
            profile.put("cra", cra);
            profile.put("riskLevel", level);
            profile.put("country", merchant.getCountry());
            profile.put("countryScore", countryScore);
            profile.put("pepSanctionsScore", pepSancScore);
            profile.put("industryScore", industryScore);
            profile.put("volumeScore", volumeScore);
            profile.put("alertScore", alertScore);
            profile.put("ageScore", ageScore);
            profile.put("decision", decisionFor(level));
            profile.put("calculatedAt", LocalDateTime.now().toString());
            try {
                amlMicroserviceClient.cacheRiskProfile(merchant.getMerchantId(), profile);
            } catch (Exception cacheEx) {
                // Already logged inside the client; never fail the CRA write because the cache is down.
                log.debug("Aerospike risk-profile cache write failed (non-fatal): {}", cacheEx.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to persist CRA for merchant {}: {}", merchant.getMerchantId(), e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CRA component scorers
    // ─────────────────────────────────────────────────────────────────────────

    /** Country risk: 0–100 from country_risk_scores (FATF blacklist=100, greylist≈70). */
    double getCountryRisk(String code) {
        if (code == null || code.isBlank()) return NEUTRAL_RISK;
        String normalized = code.trim().toUpperCase();

        Optional<CountryRiskScore> primary = countryRiskRepository.findByCountryCode(normalized);
        if (primary.isPresent() && primary.get().getRiskScore() != null) {
            return primary.get().getRiskScore().doubleValue();
        }
        return highRiskCountryRepository.findByCountryCode(normalized)
                .map(hrc -> "CRITICAL".equals(hrc.getRiskLevel()) ? 95.0 : 75.0)
                .orElse(NEUTRAL_RISK);
    }

    /** PEP / sanctions exposure across the merchant + its UBOs. */
    private double scorePepAndSanctions(Merchant merchant) {
        boolean merchantPep = merchant.isPep();

        List<BeneficialOwner> owners = beneficialOwnerRepository.findByMerchant_MerchantId(merchant.getMerchantId());
        boolean uboSanctioned = owners.stream().anyMatch(bo -> Boolean.TRUE.equals(bo.getIsSanctioned()));
        boolean uboPep        = owners.stream().anyMatch(bo -> Boolean.TRUE.equals(bo.getIsPep()));

        if (uboSanctioned) return 100.0;
        if (merchantPep || uboPep) return 70.0;
        return 0.0;
    }

    private double scoreMccRisk(String mcc) {
            if (mcc == null || mcc.isBlank()) return NEUTRAL_RISK;
            return mccRiskConfig.getRiskForMcc(mcc);
        }

    /** Compares 30-day actual volume against expected monthly volume. */
    private double scoreVolume(Merchant merchant) {
        Long expectedCents = merchant.getExpectedMonthlyVolume();
        Long actualCents = transactionRepository.sumAmountByMerchantInTimeWindow(
                String.valueOf(merchant.getMerchantId()),
                LocalDateTime.now().minusDays(volumeLookbackDays),
                LocalDateTime.now());

        if (actualCents == null) actualCents = 0L;

        if (expectedCents == null || expectedCents <= 0) {
            double absDollars = actualCents / 100.0;
            if (absDollars > 5_000_000) return 90.0;
            if (absDollars > 1_000_000) return 70.0;
            if (absDollars >    100_000) return 40.0;
            return 10.0;
        }

        double ratio = (double) actualCents / (double) expectedCents;
        if (ratio > 5.0) return 100.0;
        if (ratio > 2.0) return 80.0;
        if (ratio > 1.2) return 50.0;
        if (ratio > 0.5) return 20.0;
        return 10.0;
    }

    /** Recent alert volume tier — 90-day window. */
    private double scoreAlertHistory(Long merchantId) {
        long alerts = alertRepository.countByMerchantIdSince(
                merchantId, LocalDateTime.now().minusDays(alertLookbackDays));
        if (alerts >= 10) return 100.0;
        if (alerts >= 5)  return 75.0;
        if (alerts >= 3)  return 50.0;
        if (alerts >= 1)  return 25.0;
        return 0.0;
    }

    /** Newer businesses are riskier (less history, higher mule probability). */
    private double scoreBusinessAge(LocalDate registrationDate) {
        if (registrationDate == null) return 60.0;
        Period age = Period.between(registrationDate, LocalDate.now());
        int months = age.getYears() * 12 + age.getMonths();
        if (months <  6) return 80.0;
        if (months < 12) return 50.0;
        if (months < 24) return 20.0;
        return 10.0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private double calculateAmountRisk(BigDecimal amount) {
        if (amount == null) return 0.0;
        double val = amount.doubleValue();
        if (val < 1000)   return 10.0;
        if (val < 5000)   return 30.0;
        if (val < 10000)  return 50.0;
        if (val < 50000)  return 80.0;
        return 100.0;
    }

    private double calculateTrs(Map<String, Object> features, RuleEvaluationResult ruleResult) {
        double score = ruleResult.getTriggeredRules().size() * 20.0;
        double amount = Double.parseDouble(features.getOrDefault("amount", "0").toString());
        if (amount > 10_000) score += 30;
        String country = (String) features.getOrDefault("country_code", null);
        if (getCountryRisk(country) > 50.0) score += 40;
        return Math.min(100.0, score);
    }

    private static String riskLevelFor(double cra) {
        if (cra >= 80) return "CRITICAL";
        if (cra >= 60) return "HIGH";
        if (cra >= 40) return "MEDIUM";
        return "LOW";
    }

    private static String decisionFor(String level) {
        return switch (level) {
            case "CRITICAL" -> "REJECT";
            case "HIGH"     -> "REVIEW";
            default         -> "APPROVE";
        };
    }

    private static Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.valueOf(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal parseAmount(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(value.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static String nullSafe(String s) { return s == null ? "UNKNOWN" : s; }
}
