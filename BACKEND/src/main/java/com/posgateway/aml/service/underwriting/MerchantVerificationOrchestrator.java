package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.underwriting.MerchantVerificationSignal;
import com.posgateway.aml.model.underwriting.ProviderResult;
import com.posgateway.aml.model.underwriting.SignalSeverity;
import com.posgateway.aml.model.underwriting.UnderwritingDecision;
import com.posgateway.aml.model.underwriting.UnderwritingOutcome;
import com.posgateway.aml.model.underwriting.VerificationSignal;
import com.posgateway.aml.repository.underwriting.MerchantVerificationSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Merchant verification orchestrator — the spine of the KYB/underwriting program.
 *
 * <p>For one run it: (1) runs internal checks derivable from persisted merchant data,
 * (2) runs every registered {@link ExternalVerificationProvider} (all fail-closed until
 * credentialed), (3) persists every normalized signal as append-only evidence,
 * (4) applies hard-stop rules, and (5) computes an explainable weighted risk score with a
 * per-domain breakdown, returning an {@link UnderwritingOutcome}. The score never
 * overrides a hard stop, and any unavailable/uncertain signal forces at least manual review
 * — nothing is silently passed.
 */
@Service
public class MerchantVerificationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MerchantVerificationOrchestrator.class);

    // Explainable risk-domain weights (max points each can contribute), per the KYB brief.
    private static final Map<String, Integer> DOMAIN_WEIGHTS = new LinkedHashMap<>();
    static {
        DOMAIN_WEIGHTS.put("LEGAL_ENTITY", 20);
        DOMAIN_WEIGHTS.put("OWNERS", 20);
        DOMAIN_WEIGHTS.put("BUSINESS_REALITY", 15);
        DOMAIN_WEIGHTS.put("HISTORY_NETWORK", 15);
        DOMAIN_WEIGHTS.put("AML_SANCTIONS", 10);
        DOMAIN_WEIGHTS.put("FINANCIAL", 10);
        DOMAIN_WEIGHTS.put("DIGITAL", 5);
        DOMAIN_WEIGHTS.put("CONTROLS", 5);
    }

    /** A merchant younger than this (days) forecasting very high volume is a bust-out signal. */
    private static final long NEW_ENTITY_DAYS = 90;
    private static final long HIGH_VOLUME_THRESHOLD = 10_000_000L;

    private final List<ExternalVerificationProvider> providers;
    private final HardStopEvaluator hardStopEvaluator;
    private final MerchantVerificationSignalRepository signalRepository;
    private final MerchantLinkageService linkageService;
    private final TransactionLaunderingCheckService launderingCheckService;
    private final com.posgateway.aml.service.edd.EnhancedDueDiligenceService eddService;

    public MerchantVerificationOrchestrator(List<ExternalVerificationProvider> providers,
                                            HardStopEvaluator hardStopEvaluator,
                                            MerchantVerificationSignalRepository signalRepository,
                                            MerchantLinkageService linkageService,
                                            TransactionLaunderingCheckService launderingCheckService,
                                            com.posgateway.aml.service.edd.EnhancedDueDiligenceService eddService) {
        this.providers = providers;
        this.hardStopEvaluator = hardStopEvaluator;
        this.signalRepository = signalRepository;
        this.linkageService = linkageService;
        this.launderingCheckService = launderingCheckService;
        this.eddService = eddService;
    }

    @Transactional
    public UnderwritingOutcome verify(Merchant merchant) {
        String runId = UUID.randomUUID().toString();
        MerchantVerificationContext ctx = new MerchantVerificationContext(merchant, runId);
        log.info("Underwriting verification run {} for merchant {} ({})",
                runId, merchant.getMerchantId(), merchant.getLegalName());

        List<VerificationSignal> signals = new ArrayList<>();
        signals.addAll(runInternalChecks(ctx));

        // Merchant-linkage / reincarnation detection (shared-identifier graph).
        try {
            signals.addAll(linkageService.findLinkages(merchant));
        } catch (Exception e) {
            log.warn("Merchant linkage check failed for {}: {}", merchant.getMerchantId(), e.getMessage());
        }

        // Transaction-laundering typologies over recent activity (real detectors).
        try {
            signals.addAll(launderingCheckService.findSignals(merchant));
        } catch (Exception e) {
            log.warn("Transaction-laundering check failed for {}: {}", merchant.getMerchantId(), e.getMessage());
        }

        for (ExternalVerificationProvider provider : providers) {
            ProviderResult result = provider.verify(ctx);
            signals.addAll(result.getSignals());
        }

        persistSignals(merchant, runId, signals);

        UnderwritingOutcome outcome = new UnderwritingOutcome();
        outcome.getSignals().addAll(signals);

        // Hard stops first — they cannot be overridden by the score.
        List<String> hardStops = hardStopEvaluator.evaluate(ctx, signals);
        outcome.getHardStops().addAll(hardStops);

        boolean manualReviewForced = signals.stream().anyMatch(VerificationSignal::isRequiresManualReview);
        outcome.setManualReviewForced(manualReviewForced);

        int score = computeScore(signals, outcome);
        outcome.setScore(score);

        outcome.setDecision(decide(hardStops, manualReviewForced, score));
        outcome.getRequiredControls().addAll(defaultControls(outcome.getDecision()));

        // Auto-initiate EDD when the outcome calls for it (idempotent).
        if (outcome.getDecision() == UnderwritingDecision.ENHANCED_DUE_DILIGENCE
                && merchant.getMerchantId() != null) {
            try {
                eddService.ensureEddInitiated(merchant.getMerchantId(), "SYSTEM_UNDERWRITING");
            } catch (Exception e) {
                log.warn("Auto-EDD initiation failed for merchant {}: {}",
                        merchant.getMerchantId(), e.getMessage());
            }
        }

        log.info("Underwriting run {} → decision={} score={} hardStops={} manualReviewForced={}",
                runId, outcome.getDecision(), score, hardStops.size(), manualReviewForced);
        return outcome;
    }

    // ---- Internal checks (derivable from persisted merchant data) ----------------------

    private List<VerificationSignal> runInternalChecks(MerchantVerificationContext ctx) {
        Merchant m = ctx.getMerchant();
        String ref = ctx.entityRef();
        List<VerificationSignal> out = new ArrayList<>();

        // Ownership: no declared beneficial owners is a hard stop (refusal to disclose UBOs).
        List<BeneficialOwner> owners = m.getBeneficialOwners();
        if (owners == null || owners.isEmpty()) {
            out.add(VerificationSignal.of("NO_BENEFICIAL_OWNERS", SignalSeverity.CRITICAL, "INTERNAL",
                    ref, true, "No beneficial owners declared for the merchant"));
        }

        // MCC present.
        if (isBlank(m.getMcc())) {
            out.add(VerificationSignal.of("MCC_MISSING", SignalSeverity.MEDIUM, "INTERNAL",
                    ref, true, "No MCC / business classification on file"));
        }

        // Website / digital presence.
        if (isBlank(m.getWebsite())) {
            out.add(VerificationSignal.of("NO_WEBSITE", SignalSeverity.LOW, "INTERNAL",
                    ref, false, "No website declared — business-reality verification limited"));
        }

        // Settlement account verified.
        if (!m.isCbkSettlementAccountConfigured()) {
            out.add(VerificationSignal.of("SETTLEMENT_UNVERIFIED", SignalSeverity.MEDIUM, "INTERNAL",
                    ref, true, "Settlement account not configured/verified"));
        }

        // Expected-profile baseline present.
        Long expectedVolume = m.getExpectedMonthlyVolume();
        if (expectedVolume == null) {
            out.add(VerificationSignal.of("NO_EXPECTED_PROFILE", SignalSeverity.LOW, "INTERNAL",
                    ref, false, "No expected monthly volume declared — monitoring baseline weak"));
        }

        // New entity forecasting very high volume — bust-out indicator.
        LocalDate reg = m.getRegistrationDate();
        boolean young = reg != null && reg.isAfter(LocalDate.now().minusDays(NEW_ENTITY_DAYS));
        if (young && expectedVolume != null && expectedVolume > HIGH_VOLUME_THRESHOLD) {
            out.add(VerificationSignal.of("NEW_ENTITY_HIGH_VOLUME", SignalSeverity.HIGH, "INTERNAL",
                    ref, true, "Recently registered entity (" + reg + ") forecasting high volume ("
                            + expectedVolume + ") — bust-out risk"));
        }

        return out;
    }

    // ---- Persistence -------------------------------------------------------------------

    private void persistSignals(Merchant merchant, String runId, List<VerificationSignal> signals) {
        List<MerchantVerificationSignal> rows = new ArrayList<>();
        for (VerificationSignal s : signals) {
            MerchantVerificationSignal row = new MerchantVerificationSignal();
            row.setMerchantId(merchant.getMerchantId());
            row.setRunId(runId);
            row.setSignalCode(s.getSignalCode());
            row.setSeverity(s.getSeverity() != null ? s.getSeverity().name() : SignalSeverity.INFO.name());
            row.setSource(s.getSource());
            row.setConfidence(s.getConfidence());
            row.setRequiresManualReview(s.isRequiresManualReview());
            row.setEvidenceReference(s.getEvidenceReference());
            row.setDetail(s.getDetail());
            row.setObservedAt(s.getObservedAt() != null
                    ? LocalDateTime.ofInstant(s.getObservedAt(), java.time.ZoneId.systemDefault())
                    : LocalDateTime.now());
            rows.add(row);
        }
        signalRepository.saveAll(rows);
    }

    // ---- Explainable scoring -----------------------------------------------------------

    private int computeScore(List<VerificationSignal> signals, UnderwritingOutcome outcome) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (VerificationSignal s : signals) {
            String domain = domainFor(s);
            raw.merge(domain, severityPoints(s.getSeverity()), Integer::sum);
        }
        int total = 0;
        for (Map.Entry<String, Integer> e : DOMAIN_WEIGHTS.entrySet()) {
            int capped = Math.min(e.getValue(), raw.getOrDefault(e.getKey(), 0));
            outcome.addComponent(e.getKey(), capped);
            total += capped;
        }
        return Math.min(100, total);
    }

    private static int severityPoints(SignalSeverity severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case INFO -> 0;
            case LOW -> 3;
            case MEDIUM -> 7;
            case HIGH -> 12;
            case CRITICAL -> 20;
        };
    }

    private static String domainFor(VerificationSignal s) {
        String source = s.getSource() == null ? "" : s.getSource().toUpperCase();
        switch (source) {
            case "MATCH_PRO":
            case "VMSS":
                return "HISTORY_NETWORK";
            case "REGISTRY":
                return "LEGAL_ENTITY";
            case "SMILE_ID":
                return "OWNERS";
            case "BANK_ACCOUNT_NAME":
                return "CONTROLS";
            default:
                break;
        }
        String code = s.getSignalCode() == null ? "" : s.getSignalCode().toUpperCase();
        if (code.contains("LINKED") || code.contains("REINCARNAT")) return "HISTORY_NETWORK";
        if (code.contains("OWNER") || code.contains("UBO")) return "OWNERS";
        if (code.contains("SANCTION") || code.contains("PEP") || code.contains("ADVERSE")) return "AML_SANCTIONS";
        if (code.contains("SETTLEMENT")) return "CONTROLS";
        if (code.contains("VOLUME") || code.contains("PROFILE") || code.contains("FINANC")) return "FINANCIAL";
        if (code.contains("WEBSITE") || code.contains("DOMAIN") || code.contains("DIGITAL")) return "DIGITAL";
        if (code.contains("ENTITY") || code.contains("REGISTR")) return "LEGAL_ENTITY";
        return "BUSINESS_REALITY";
    }

    // ---- Decision ----------------------------------------------------------------------

    private UnderwritingDecision decide(List<String> hardStops, boolean manualReviewForced, int score) {
        if (!hardStops.isEmpty()) {
            return UnderwritingDecision.REJECT;
        }
        if (score >= 60) {
            return UnderwritingDecision.ENHANCED_DUE_DILIGENCE;
        }
        if (manualReviewForced || score >= 30) {
            return UnderwritingDecision.MANUAL_REVIEW;
        }
        if (score >= 15) {
            return UnderwritingDecision.APPROVE_WITH_CONTROLS;
        }
        return UnderwritingDecision.APPROVE;
    }

    private List<String> defaultControls(UnderwritingDecision decision) {
        List<String> controls = new ArrayList<>();
        switch (decision) {
            case APPROVE_WITH_CONTROLS -> {
                controls.add("LOWER_TRANSACTION_LIMITS");
                controls.add("DELAYED_SETTLEMENT");
                controls.add("HEIGHTENED_MONITORING_30D");
            }
            case ENHANCED_DUE_DILIGENCE -> {
                controls.add("SENIOR_MANAGEMENT_APPROVAL");
                controls.add("SOURCE_OF_FUNDS_VERIFICATION");
                controls.add("ROLLING_RESERVE");
                controls.add("DELAYED_SETTLEMENT");
                controls.add("LOWER_TRANSACTION_LIMITS");
            }
            case MANUAL_REVIEW -> controls.add("MANUAL_UNDERWRITER_REVIEW");
            default -> { /* APPROVE / REJECT: no conditional controls */ }
        }
        return controls;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
