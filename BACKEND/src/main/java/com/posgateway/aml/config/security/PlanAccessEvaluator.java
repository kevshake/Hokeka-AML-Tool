package com.posgateway.aml.config.security;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.psp.EntitlementService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Method-security helper for plan-based feature gating. Register a gate on any endpoint that a plan
 * feature should govern:
 *
 * <pre>{@code @PreAuthorize("@planAccess.hasFeature(authentication, 'custom_rules')")}</pre>
 *
 * <p>Platform/admin principals (no {@code pspId}) are never plan-gated. A PSP with no active
 * subscription is grandfathered by {@link EntitlementService#hasFeature}; a subscribed PSP is held
 * to exactly the features its tier lists.
 */
@Component("planAccess")
public class PlanAccessEvaluator {

    private final EntitlementService entitlementService;

    public PlanAccessEvaluator(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    public boolean hasFeature(Authentication authentication, String feature) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return false;
        }
        Long pspId = user.getPsp() != null ? user.getPsp().getPspId() : null;
        if (pspId == null) {
            return true; // platform/admin user — not plan-gated
        }
        return entitlementService.hasFeature(pspId, feature);
    }
}
