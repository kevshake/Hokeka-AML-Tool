package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.residency.EdgeMerchantBlocklistEntry;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.residency.EdgeMerchantBlocklistRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.sanctions.SanctionsHitClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the Edge merchant blocklist in step with cloud merchant screening (R10b, plan "Conflicts
 * and resolutions" item 1): merchants are screened in the cloud at onboarding and on periodic
 * rescreen, and a confirmed sanctions hit reaches the PSP's Edges as a {@code SANCTIONED} entry of
 * {@code edge_merchant_blocklist}, which the Edge enforces on every evaluation.
 *
 * <ul>
 *   <li>{@link SanctionsHitClassifier.Verdict#CONFIRMED}: an active {@code SANCTIONED} block is added
 *       (unless the merchant is already blocked for any reason).</li>
 *   <li>{@link SanctionsHitClassifier.Verdict#CLEAR} (no sanctions-list match; PEP-only matches are
 *       clear): an active {@code SANCTIONED} block is lifted. Blocks with another reason (regulator
 *       order, termination, fraud) are never lifted here.</li>
 *   <li>{@code POTENTIAL} and {@code UNKNOWN} (screen unavailable): nothing changes.</li>
 * </ul>
 *
 * <p>Every change is audited and announced with {@link PspDataSettingsChangedEvent}, so
 * {@link EdgeConfigService} mints a new configVersion and {@code notify config.updated} goes out.
 */
@Service
public class MerchantSanctionsBlocklistService {

    private static final Logger log = LoggerFactory.getLogger(MerchantSanctionsBlocklistService.class);

    /** Actor recorded on automatic blocklist rows. */
    static final String ACTOR = "system:merchant-screening";
    static final String SANCTIONED = "SANCTIONED";

    private final EdgeMerchantBlocklistRepository blocklist;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    private final double confirmedThreshold;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public MerchantSanctionsBlocklistService(EdgeMerchantBlocklistRepository blocklist, AuditLogService audit,
                                             ApplicationEventPublisher events,
                                             @Value("${screening.realtime.confirmed-match-threshold:0.95}")
                                             double confirmedThreshold) {
        this(blocklist, audit, events, confirmedThreshold, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock. */
    public MerchantSanctionsBlocklistService(EdgeMerchantBlocklistRepository blocklist, AuditLogService audit,
                                             ApplicationEventPublisher events, double confirmedThreshold,
                                             Clock clock) {
        this.blocklist = blocklist;
        this.audit = audit;
        this.events = events;
        this.confirmedThreshold = confirmedThreshold;
        this.clock = clock;
    }

    /** What {@link #apply} did. */
    public enum Change {
        /** A SANCTIONED block was added. */
        BLOCKED,
        /** A SANCTIONED block was lifted. */
        UNBLOCKED,
        /** Nothing changed. */
        NONE
    }

    /**
     * Applies one merchant screening result to the merchant's PSP blocklist.
     *
     * @param merchant the screened merchant (must belong to a PSP to be blocked)
     * @param result   its screening result (legal and trading name)
     */
    @Transactional
    public Change apply(Merchant merchant, ScreeningResult result) {
        if (merchant == null || merchant.getMerchantId() == null || merchant.getPsp() == null
                || merchant.getPsp().getPspId() == null) {
            return Change.NONE;
        }
        Long pspId = merchant.getPsp().getPspId();
        String merchantId = merchant.getMerchantId().toString();
        SanctionsHitClassifier.Verdict verdict = SanctionsHitClassifier.classify(result, confirmedThreshold);
        Optional<EdgeMerchantBlocklistEntry> active = blocklist.findFirstByPspIdAndMerchantIdAndRemovedAtIsNull(pspId,
                merchantId);
        switch (verdict) {
            case CONFIRMED -> {
                if (active.isPresent()) {
                    return Change.NONE;
                }
                EdgeMerchantBlocklistEntry entry = new EdgeMerchantBlocklistEntry();
                entry.setPspId(pspId);
                entry.setMerchantId(merchantId);
                entry.setReasonCode(SANCTIONED);
                entry.setSince(clock.instant());
                entry.setCreatedBy(ACTOR);
                blocklist.save(entry);
                audit.logAction(null, "EDGE_MERCHANT_BLOCKED", "MERCHANT", merchantId, null,
                        Map.of("pspId", pspId, "merchantId", merchantId, "reasonCode", SANCTIONED), null,
                        "Confirmed sanctions hit in merchant screening");
                events.publishEvent(new PspDataSettingsChangedEvent(pspId, "MERCHANT_BLOCKLIST"));
                log.warn("Merchant {} of PSP {} blocked on the Edge: confirmed sanctions hit", merchantId, pspId);
                return Change.BLOCKED;
            }
            case CLEAR -> {
                if (active.isEmpty() || !SANCTIONED.equals(active.get().getReasonCode())) {
                    return Change.NONE;
                }
                EdgeMerchantBlocklistEntry entry = active.get();
                Instant now = clock.instant();
                entry.setRemovedAt(now);
                entry.setRemovedBy(ACTOR);
                blocklist.save(entry);
                audit.logAction(null, "EDGE_MERCHANT_UNBLOCKED", "MERCHANT", merchantId,
                        Map.of("pspId", pspId, "merchantId", merchantId, "reasonCode", SANCTIONED), null, null,
                        "Merchant rescreen is clear of sanctions-list matches");
                events.publishEvent(new PspDataSettingsChangedEvent(pspId, "MERCHANT_BLOCKLIST"));
                log.info("Merchant {} of PSP {} unblocked on the Edge: rescreen is clear", merchantId, pspId);
                return Change.UNBLOCKED;
            }
            default -> {
                return Change.NONE;
            }
        }
    }
}
