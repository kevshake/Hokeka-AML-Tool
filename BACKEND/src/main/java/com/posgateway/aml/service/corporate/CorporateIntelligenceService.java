package com.posgateway.aml.service.corporate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.client.corporate.GdeltAdverseMediaClient;
import com.posgateway.aml.client.corporate.OpenCorporatesClient;
import com.posgateway.aml.dto.corporate.CorporateIntelligenceDtos.CheckResponse;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.corporate.*;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.corporate.CorporateIntelligenceCheckRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CorporateIntelligenceService {
    private static final String ALERT_SOURCE = "CORPORATE_INTELLIGENCE";

    private final MerchantRepository merchantRepository;
    private final CorporateIntelligenceCheckRepository checkRepository;
    private final AlertRepository alertRepository;
    private final OpenCorporatesClient registryClient;
    private final GdeltAdverseMediaClient adverseMediaClient;
    private final PspIsolationService isolationService;
    private final ObjectMapper objectMapper;
    private final JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();
    private final int clearReviewDays;
    private final int exceptionReviewDays;
    private final int batchSize;

    public CorporateIntelligenceService(
            MerchantRepository merchantRepository,
            CorporateIntelligenceCheckRepository checkRepository,
            AlertRepository alertRepository,
            OpenCorporatesClient registryClient,
            GdeltAdverseMediaClient adverseMediaClient,
            PspIsolationService isolationService,
            ObjectMapper objectMapper,
            @Value("${corporate.intelligence.clear-review-days:30}") int clearReviewDays,
            @Value("${corporate.intelligence.exception-review-days:3}") int exceptionReviewDays,
            @Value("${corporate.intelligence.batch-size:100}") int batchSize) {
        this.merchantRepository = merchantRepository;
        this.checkRepository = checkRepository;
        this.alertRepository = alertRepository;
        this.registryClient = registryClient;
        this.adverseMediaClient = adverseMediaClient;
        this.isolationService = isolationService;
        this.objectMapper = objectMapper;
        this.clearReviewDays = Math.max(1, clearReviewDays);
        this.exceptionReviewDays = Math.max(1, exceptionReviewDays);
        this.batchSize = Math.min(Math.max(batchSize, 1), 500);
    }

    @Transactional
    public CheckResponse runManualCheck(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        User user = isolationService.getCurrentUser();
        return toResponse(performCheck(merchant, "MANUAL", user == null ? "SYSTEM" : user.getUsername()));
    }

    @Transactional(readOnly = true)
    public List<CheckResponse> history(Long merchantId) {
        requireMerchant(merchantId);
        return checkRepository.findByMerchant_MerchantIdOrderByCheckedAtDesc(merchantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CheckResponse> latest(Long merchantId) {
        requireMerchant(merchantId);
        return checkRepository.findFirstByMerchant_MerchantIdOrderByCheckedAtDesc(merchantId).map(this::toResponse);
    }

    @Transactional
    public CorporateIntelligenceCheck performCheck(Merchant merchant, String checkType, String actor) {
        Objects.requireNonNull(merchant, "merchant");
        if (merchant.getPsp() == null || merchant.getPsp().getPspId() == null) {
            throw new IllegalStateException("Merchant must belong to a PSP before corporate intelligence runs");
        }
        var registry = registryClient.search(
                merchant.getLegalName(), merchant.getCountry(), merchant.getRegistrationNumber());
        var adverse = adverseMediaClient.search(merchant.getLegalName(), merchant.getTradingName());

        RegistryAssessment registryAssessment = assessRegistry(merchant, registry);
        AdverseMediaStatus mediaStatus = !adverse.available()
                ? AdverseMediaStatus.UNAVAILABLE
                : adverse.articles().isEmpty() ? AdverseMediaStatus.CLEAR : AdverseMediaStatus.HITS;
        CorporateIntelligenceStatus status = overallStatus(registryAssessment, mediaStatus);
        int riskScore = calculateRisk(registryAssessment, mediaStatus, adverse.articles().size());
        String reason = decisionReason(registryAssessment, registry, mediaStatus, adverse);

        CorporateIntelligenceCheck check = new CorporateIntelligenceCheck();
        check.setPspId(merchant.getPsp().getPspId());
        check.setMerchant(merchant);
        check.setCheckType(normalizeCheckType(checkType));
        check.setStatus(status);
        check.setRegistryStatus(registryAssessment.status());
        check.setRegistryProvider(registry.provider());
        check.setRegistryMatchScore(registryAssessment.score());
        if (registryAssessment.candidate() != null) {
            var candidate = registryAssessment.candidate();
            check.setMatchedCompanyName(candidate.name());
            check.setMatchedCompanyNumber(candidate.companyNumber());
            check.setMatchedJurisdiction(candidate.jurisdictionCode());
            check.setMatchedCompanyStatus(candidate.currentStatus());
            check.setMatchedCompanyUrl(candidate.sourceUrl());
        }
        check.setRegistryCandidates(registry.candidates().stream().map(OpenCorporatesClient.CompanyCandidate::asEvidence).toList());
        check.setRegistryProvenance(withAvailability(registry.provenance(), registry.available(), registry.unavailableReason()));
        check.setAdverseMediaStatus(mediaStatus);
        check.setAdverseMediaProvider(adverse.provider());
        check.setAdverseMediaQuery(adverse.query());
        check.setAdverseMediaArticleCount(adverse.articles().size());
        check.setAdverseMediaArticles(adverse.articles().stream().map(GdeltAdverseMediaClient.Article::asEvidence).toList());
        check.setAdverseMediaProvenance(withAvailability(adverse.provenance(), adverse.available(), adverse.unavailableReason()));
        check.setRiskScore(riskScore);
        check.setDecisionReason(reason);
        check.setCheckedBy(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        check.setCheckedAt(LocalDateTime.now());
        check.setEvidenceHash(evidenceHash(check));
        check = checkRepository.save(check);

        merchant.setLastCorporateIntelligenceAt(check.getCheckedAt());
        merchant.setNextCorporateIntelligenceDue(check.getCheckedAt().plusDays(
                status == CorporateIntelligenceStatus.CLEAR ? clearReviewDays : exceptionReviewDays));
        if (status != CorporateIntelligenceStatus.CLEAR && "ACTIVE".equalsIgnoreCase(merchant.getStatus())) {
            merchant.setStatus("UNDER_REVIEW");
        }
        merchantRepository.save(merchant);
        if (status != CorporateIntelligenceStatus.CLEAR) createAlert(check);
        return check;
    }

    @Transactional(readOnly = true)
    public List<Long> dueMerchantIds() {
        return merchantRepository.findDueForCorporateIntelligence(
                        LocalDateTime.now(), PageRequest.of(0, batchSize)).stream()
                .map(Merchant::getMerchantId)
                .toList();
    }

    @Transactional
    public void runScheduledCheck(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        performCheck(merchant, "PERIODIC", "SYSTEM_SCHEDULER");
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        isolationService.validateMerchantAccess(merchant);
        return merchant;
    }

    private RegistryAssessment assessRegistry(Merchant merchant,
            OpenCorporatesClient.RegistrySearchResult result) {
        if (!result.available()) return new RegistryAssessment(CorporateRegistryStatus.UNAVAILABLE, 0, null, false);
        OpenCorporatesClient.CompanyCandidate best = null;
        int bestScore = 0;
        for (var candidate : result.candidates()) {
            int score = registryScore(merchant, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) return new RegistryAssessment(CorporateRegistryStatus.NO_MATCH, 0, null, false);
        boolean active = isActive(best.currentStatus());
        boolean exactRegistration = normalized(merchant.getRegistrationNumber())
                .equals(normalized(best.companyNumber()));
        double nameScore = nameSimilarity(merchant.getLegalName(), best.name());
        CorporateRegistryStatus status = exactRegistration && nameScore >= 0.70
                ? CorporateRegistryStatus.MATCH
                : bestScore >= 70 ? CorporateRegistryStatus.POTENTIAL_MATCH : CorporateRegistryStatus.NO_MATCH;
        return new RegistryAssessment(status, bestScore, best, active);
    }

    private int registryScore(Merchant merchant, OpenCorporatesClient.CompanyCandidate candidate) {
        int score = 0;
        if (!normalized(merchant.getRegistrationNumber()).isBlank()
                && normalized(merchant.getRegistrationNumber()).equals(normalized(candidate.companyNumber()))) {
            score += 60;
        }
        score += (int) Math.round(nameSimilarity(merchant.getLegalName(), candidate.name()) * 30);
        String country = merchant.getCountry() == null ? "" : merchant.getCountry().toLowerCase(Locale.ROOT);
        String jurisdiction = candidate.jurisdictionCode() == null ? "" : candidate.jurisdictionCode().toLowerCase(Locale.ROOT);
        if (!country.isBlank() && (jurisdiction.equals(country) || jurisdiction.startsWith(country.substring(0, Math.min(2, country.length()))))) {
            score += 10;
        }
        return Math.min(score, 100);
    }

    private double nameSimilarity(String left, String right) {
        if (left == null || right == null) return 0;
        Double value = similarity.apply(normalizedName(left), normalizedName(right));
        return value == null ? 0 : value;
    }

    private String normalizedName(String value) {
        return normalized(value)
                .replace("limited", "")
                .replace("ltd", "")
                .replace("incorporated", "")
                .replace("inc", "")
                .replace("company", "")
                .replace("corp", "")
                .trim();
    }

    private String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean isActive(String status) {
        if (status == null || status.isBlank()) return true;
        String normalized = status.toLowerCase(Locale.ROOT);
        return !(normalized.contains("inactive") || normalized.contains("dissolved")
                || normalized.contains("liquidat") || normalized.contains("struck")
                || normalized.contains("closed") || normalized.contains("cancel"));
    }

    private CorporateIntelligenceStatus overallStatus(RegistryAssessment registry,
            AdverseMediaStatus mediaStatus) {
        if (registry.status() == CorporateRegistryStatus.NO_MATCH
                || registry.status() == CorporateRegistryStatus.POTENTIAL_MATCH
                || (registry.status() == CorporateRegistryStatus.MATCH && !registry.active())
                || mediaStatus == AdverseMediaStatus.HITS) {
            return CorporateIntelligenceStatus.REVIEW;
        }
        if (registry.status() == CorporateRegistryStatus.UNAVAILABLE
                || mediaStatus == AdverseMediaStatus.UNAVAILABLE) {
            return CorporateIntelligenceStatus.UNAVAILABLE;
        }
        return CorporateIntelligenceStatus.CLEAR;
    }

    private int calculateRisk(RegistryAssessment registry, AdverseMediaStatus mediaStatus, int articleCount) {
        int score = switch (registry.status()) {
            case MATCH -> registry.active() ? 0 : 50;
            case POTENTIAL_MATCH -> 40;
            case NO_MATCH -> 55;
            case UNAVAILABLE -> 35;
        };
        if (mediaStatus == AdverseMediaStatus.HITS) score = Math.max(score, Math.min(85, 45 + articleCount * 5));
        if (mediaStatus == AdverseMediaStatus.UNAVAILABLE) score = Math.max(score, 35);
        return Math.min(score, 100);
    }

    private String decisionReason(RegistryAssessment registry,
            OpenCorporatesClient.RegistrySearchResult registryResult,
            AdverseMediaStatus mediaStatus,
            GdeltAdverseMediaClient.AdverseMediaResult adverse) {
        List<String> reasons = new ArrayList<>();
        switch (registry.status()) {
            case MATCH -> {
                reasons.add(registry.active()
                        ? "Corporate registry identity matched"
                        : "Corporate registry match is not active");
            }
            case POTENTIAL_MATCH -> reasons.add("Corporate registry returned a potential identity match requiring review");
            case NO_MATCH -> reasons.add("No sufficiently reliable corporate registry match was found");
            case UNAVAILABLE -> reasons.add(registryResult.unavailableReason());
        }
        switch (mediaStatus) {
            case CLEAR -> reasons.add("No adverse-media leads were returned for the configured one-year query");
            case HITS -> reasons.add(adverse.articles().size() + " adverse-media lead(s) require investigator review");
            case UNAVAILABLE -> reasons.add(adverse.unavailableReason());
        }
        return String.join("; ", reasons);
    }

    private Map<String, Object> withAvailability(Map<String, Object> provenance,
            boolean available, String unavailableReason) {
        Map<String, Object> value = new LinkedHashMap<>(provenance == null ? Map.of() : provenance);
        value.put("available", available);
        if (unavailableReason != null) value.put("unavailableReason", unavailableReason);
        return value;
    }

    private String evidenceHash(CorporateIntelligenceCheck check) {
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("merchantId", check.getMerchant().getMerchantId());
            evidence.put("pspId", check.getPspId());
            evidence.put("checkType", check.getCheckType());
            evidence.put("status", check.getStatus());
            evidence.put("registryStatus", check.getRegistryStatus());
            evidence.put("registryMatchScore", check.getRegistryMatchScore());
            evidence.put("registryCandidates", check.getRegistryCandidates());
            evidence.put("registryProvenance", check.getRegistryProvenance());
            evidence.put("adverseMediaStatus", check.getAdverseMediaStatus());
            evidence.put("adverseMediaArticles", check.getAdverseMediaArticles());
            evidence.put("adverseMediaProvenance", check.getAdverseMediaProvenance());
            evidence.put("riskScore", check.getRiskScore());
            evidence.put("decisionReason", check.getDecisionReason());
            evidence.put("checkedAt", check.getCheckedAt());
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(evidence).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash corporate-intelligence evidence", exception);
        }
    }

    private void createAlert(CorporateIntelligenceCheck check) {
        String sourceReference = String.valueOf(check.getId());
        if (alertRepository.existsByPspIdAndSourceTypeAndSourceReference(check.getPspId(), ALERT_SOURCE, sourceReference)) {
            return;
        }
        Alert alert = new Alert();
        alert.setPspId(check.getPspId());
        alert.setMerchantId(check.getMerchant().getMerchantId());
        alert.setSourceType(ALERT_SOURCE);
        alert.setSourceReference(sourceReference);
        alert.setScore(check.getRiskScore() / 100.0);
        alert.setAction("REVIEW");
        alert.setStatus("open");
        alert.setSeverity(check.getRiskScore() >= 70 ? "CRITICAL" : "WARN");
        alert.setReason("Corporate intelligence: " + check.getDecisionReason());
        alertRepository.save(alert);
    }

    private String normalizeCheckType(String value) {
        String normalized = value == null ? "MANUAL" : value.trim().toUpperCase(Locale.ROOT);
        return Set.of("ONBOARDING", "PERIODIC", "MANUAL", "UPDATE").contains(normalized) ? normalized : "MANUAL";
    }

    private CheckResponse toResponse(CorporateIntelligenceCheck check) {
        return new CheckResponse(check.getId(), check.getMerchant().getMerchantId(), check.getCheckType(),
                check.getStatus(), check.getRegistryStatus(), check.getRegistryProvider(),
                check.getRegistryMatchScore(), check.getMatchedCompanyName(), check.getMatchedCompanyNumber(),
                check.getMatchedJurisdiction(), check.getMatchedCompanyStatus(), check.getMatchedCompanyUrl(),
                check.getRegistryCandidates(), check.getRegistryProvenance(), check.getAdverseMediaStatus(),
                check.getAdverseMediaProvider(), check.getAdverseMediaQuery(), check.getAdverseMediaArticleCount(),
                check.getAdverseMediaArticles(), check.getAdverseMediaProvenance(), check.getRiskScore(),
                check.getDecisionReason(), check.getEvidenceHash(), check.getCheckedBy(), check.getCheckedAt(),
                check.getRetainUntil());
    }

    private record RegistryAssessment(CorporateRegistryStatus status, int score,
                                      OpenCorporatesClient.CompanyCandidate candidate, boolean active) {}
}
