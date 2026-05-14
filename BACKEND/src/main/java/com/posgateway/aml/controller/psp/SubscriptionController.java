package com.posgateway.aml.controller.psp;

import com.posgateway.aml.dto.billing.SubscriptionRequest;
import com.posgateway.aml.dto.billing.SubscriptionResponse;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.billing.BillingCalculation;
import com.posgateway.aml.entity.billing.PricingTier;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.BillingCalculationRepository;
import com.posgateway.aml.repository.PricingTierRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SubscriptionController
 *
 * Manages PSP subscriptions to pricing tiers.
 *
 * RBAC rules:
 *   - GET own subscription — isAuthenticated() (filtered to own PSP)
 *   - GET all — SUPER_ADMIN, ADMIN
 *   - POST / PUT / DELETE — SUPER_ADMIN, ADMIN
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PricingTierRepository pricingTierRepository;
    private final PspRepository pspRepository;
    private final BillingCalculationRepository billingCalculationRepository;

    public SubscriptionController(SubscriptionRepository subscriptionRepository,
                                  PricingTierRepository pricingTierRepository,
                                  PspRepository pspRepository,
                                  BillingCalculationRepository billingCalculationRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.pspRepository = pspRepository;
        this.billingCalculationRepository = billingCalculationRepository;
    }

    // =========================================================================
    // LIST
    // =========================================================================

    /**
     * GET /subscriptions
     * ADMIN/SUPER_ADMIN returns all subscriptions.
     * PSP_ADMIN returns their own PSP's subscriptions.
     */
    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> listSubscriptions(
            @AuthenticationPrincipal User currentUser) {
        List<Subscription> subscriptions;
        if (hasAdminRole(currentUser)) {
            subscriptions = subscriptionRepository.findAll();
        } else {
            Long pspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (pspId == null) {
                return ResponseEntity.ok(List.of());
            }
            subscriptions = subscriptionRepository.findByPspId(pspId);
        }
        List<SubscriptionResponse> response = subscriptions.stream()
                .map(SubscriptionResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * POST /subscriptions — create a subscription (ADMIN only).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @RequestBody SubscriptionRequest request) {
        if (request.getPspId() == null || request.getTierCode() == null
                || request.getContractStart() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Psp> pspOpt = pspRepository.findById(request.getPspId());
        if (pspOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<PricingTier> tierOpt = pricingTierRepository.findByTierCode(request.getTierCode());
        if (tierOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Subscription subscription = new Subscription();
        subscription.setPsp(pspOpt.get());
        subscription.setPricingTier(tierOpt.get());
        subscription.setContractStart(request.getContractStart());
        subscription.setContractEnd(request.getContractEnd());
        subscription.setBillingCycle(
                request.getBillingCycle() != null ? request.getBillingCycle() : "MONTHLY");
        subscription.setBillingCurrency(
                request.getBillingCurrency() != null ? request.getBillingCurrency() : "USD");
        subscription.setDiscountPercentage(
                request.getDiscountPercentage() != null ? request.getDiscountPercentage() : BigDecimal.ZERO);
        subscription.setNotes(request.getNotes());
        subscription.setTrialEndsAt(request.getTrialEndsAt());

        // If trial end date is in the future, set status to TRIAL
        if (request.getTrialEndsAt() != null && request.getTrialEndsAt().isAfter(LocalDate.now())) {
            subscription.setStatus("TRIAL");
        } else {
            subscription.setStatus("ACTIVE");
        }

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Created subscription {} for PSP {} on tier {}",
                saved.getSubscriptionId(), request.getPspId(), request.getTierCode());

        return ResponseEntity.ok(SubscriptionResponse.from(saved));
    }

    // =========================================================================
    // GET SINGLE
    // =========================================================================

    /**
     * GET /subscriptions/{id} — single subscription detail.
     * PSP_ADMIN can only access their own PSP's subscription.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable Long id,
                                                                 @AuthenticationPrincipal User currentUser) {
        Optional<Subscription> opt = subscriptionRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Subscription subscription = opt.get();
        if (!hasAdminRole(currentUser)) {
            Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (ownPspId == null || !ownPspId.equals(subscription.getPsp().getPspId())) {
                return ResponseEntity.status(403).build();
            }
        }
        return ResponseEntity.ok(SubscriptionResponse.from(subscription));
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * PUT /subscriptions/{id} — update subscription (ADMIN only).
     * Allows changing tier, dates, discount, currency, cycle, notes.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<SubscriptionResponse> updateSubscription(@PathVariable Long id,
                                                                    @RequestBody SubscriptionRequest request) {
        Optional<Subscription> opt = subscriptionRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Subscription subscription = opt.get();

        if (request.getTierCode() != null) {
            Optional<PricingTier> tierOpt = pricingTierRepository.findByTierCode(request.getTierCode());
            if (tierOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            subscription.setPricingTier(tierOpt.get());
        }
        if (request.getBillingCycle() != null) {
            subscription.setBillingCycle(request.getBillingCycle());
        }
        if (request.getBillingCurrency() != null) {
            subscription.setBillingCurrency(request.getBillingCurrency());
        }
        if (request.getDiscountPercentage() != null) {
            subscription.setDiscountPercentage(request.getDiscountPercentage());
        }
        if (request.getContractStart() != null) {
            subscription.setContractStart(request.getContractStart());
        }
        if (request.getContractEnd() != null) {
            subscription.setContractEnd(request.getContractEnd());
        }
        if (request.getNotes() != null) {
            subscription.setNotes(request.getNotes());
        }
        if (request.getTrialEndsAt() != null) {
            subscription.setTrialEndsAt(request.getTrialEndsAt());
        }

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Updated subscription {}", id);
        return ResponseEntity.ok(SubscriptionResponse.from(saved));
    }

    // =========================================================================
    // DELETE / CANCEL
    // =========================================================================

    /**
     * DELETE /subscriptions/{id} — cancel subscription (ADMIN only).
     * Sets status to CANCELLED rather than hard-deleting.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(@PathVariable Long id) {
        Optional<Subscription> opt = subscriptionRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Subscription subscription = opt.get();
        subscription.setStatus("CANCELLED");
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Cancelled subscription {}", id);
        return ResponseEntity.ok(SubscriptionResponse.from(saved));
    }

    // =========================================================================
    // GET ACTIVE BY PSP
    // =========================================================================

    /**
     * GET /subscriptions/psp/{pspId} — active subscription for a specific PSP.
     * PSP_ADMIN scoped to own PSP.
     */
    @GetMapping("/psp/{pspId}")
    public ResponseEntity<SubscriptionResponse> getActiveSubscriptionForPsp(
            @PathVariable Long pspId,
            @AuthenticationPrincipal User currentUser) {
        if (!hasAdminRole(currentUser)) {
            Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (!pspId.equals(ownPspId)) {
                return ResponseEntity.status(403).build();
            }
        }
        Optional<Subscription> opt = subscriptionRepository.findActiveByPspId(pspId);
        return opt.map(s -> ResponseEntity.ok(SubscriptionResponse.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // USAGE HISTORY FOR SUBSCRIPTION
    // =========================================================================

    /**
     * GET /subscriptions/{id}/usage-history — billing calculations for this subscription.
     * Provides the historical billing records tied to the subscription's PSP.
     */
    @GetMapping("/{id}/usage-history")
    public ResponseEntity<List<BillingCalculation>> getUsageHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        Optional<Subscription> opt = subscriptionRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Subscription subscription = opt.get();
        if (!hasAdminRole(currentUser)) {
            Long ownPspId = currentUser.getPsp() != null ? currentUser.getPsp().getPspId() : null;
            if (ownPspId == null || !ownPspId.equals(subscription.getPsp().getPspId())) {
                return ResponseEntity.status(403).build();
            }
        }
        Long pspId = subscription.getPsp().getPspId();
        List<BillingCalculation> history = billingCalculationRepository.findByPspId(pspId);
        return ResponseEntity.ok(history);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private boolean hasAdminRole(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String roleName = user.getRole().getName();
        return "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName);
    }
}
