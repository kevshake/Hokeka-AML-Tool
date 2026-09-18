package com.posgateway.aml.service.psp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.posgateway.aml.entity.billing.PricingTier;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * Resolves what a PSP's current plan actually grants — the feature entitlements and the monthly
 * check quota — from its live {@link Subscription} → {@link PricingTier}, not from the free-text
 * {@code Psp.billingPlan} string. This is the single source of truth for runtime plan gating and
 * quota enforcement.
 *
 * <p>Results are cached for 60 s so the hot request path never hits the database per call; a plan
 * change is picked up within a minute (or immediately via {@link #invalidate(Long)}).
 *
 * <p><b>No active subscription is treated as unlimited-but-featureless</b> (quota is a commercial
 * limit, not a security control, and most tenants are not yet on a paid plan): such a PSP is not
 * blocked by quota, but is granted no premium features. Once onboarding assigns every tenant a
 * default tier this naturally tightens.
 */
@Service
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;

    private final Cache<Long, Entitlements> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    public EntitlementService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Immutable snapshot of a PSP's plan grants. {@code maxChecksPerMonth == null} = unlimited. */
    public record Entitlements(String planCode, Set<String> features, Integer maxChecksPerMonth) {
        public boolean unlimited() {
            return maxChecksPerMonth == null || maxChecksPerMonth <= 0;
        }
    }

    /** No active subscription: no premium features, no monthly cap (not blocked). */
    static final Entitlements NONE = new Entitlements("NONE", Set.of(), null);

    public Entitlements forPsp(Long pspId) {
        if (pspId == null) {
            return NONE;
        }
        return cache.get(pspId, this::load);
    }

    private Entitlements load(Long pspId) {
        Subscription sub = subscriptionRepository.findActiveByPspId(pspId).orElse(null);
        if (sub == null || sub.getPricingTier() == null) {
            return NONE;
        }
        PricingTier tier = sub.getPricingTier();
        Set<String> features = tier.getFeatures() == null ? Set.of() : Set.copyOf(tier.getFeatures());
        return new Entitlements(tier.getTierCode(), features, tier.getMaxChecksPerMonth());
    }

    /**
     * Whether the PSP's plan grants a named feature. A PSP with no active subscription is
     * grandfathered (returns {@code true}) so enforcement does not lock out tenants who predate
     * plan assignment; a subscribed PSP is held to exactly what its tier lists.
     */
    public boolean hasFeature(Long pspId, String feature) {
        Entitlements e = forPsp(pspId);
        if ("NONE".equals(e.planCode())) {
            return true; // grandfathered — no plan assigned yet
        }
        return e.features().contains(feature);
    }

    public Integer monthlyCheckLimit(Long pspId) {
        return forPsp(pspId).maxChecksPerMonth();
    }

    public String planCode(Long pspId) {
        return forPsp(pspId).planCode();
    }

    /** Invalidate the cached entitlements for a PSP after a plan/subscription change. */
    public void invalidate(Long pspId) {
        if (pspId != null) {
            cache.invalidate(pspId);
        }
    }
}
