package com.posgateway.aml.controller.psp;

import com.posgateway.aml.entity.psp.WebhookSubscription;
import com.posgateway.aml.repository.WebhookSubscriptionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * W36-2 fix: PSP-facing CRUD over {@link WebhookSubscription}. The entity, repository (already
 * correctly tenant-scoped — see {@link WebhookSubscriptionRepository#findByPspIdAndEventTypeAndIsActiveTrue})
 * and {@code WebhookService.sendWebhook} delivery path all existed with no controller exposing a
 * subscribe endpoint at all — PSP_API_GUIDE.md documented {@code POST /webhooks/subscribe} but it
 * 404'd.
 *
 * <p>Every operation is scoped to the caller's own PSP via {@link PspIsolationService} — a PSP can
 * create, list, and delete only its own subscriptions, never another tenant's. Platform admins
 * (PSP id 0) can act on any subscription for support/debugging purposes.
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/webhooks")
public class WebhookSubscriptionController {

    /** Matches the event types the entity's own field comment documents as valid. */
    private static final Set<String> VALID_EVENT_TYPES = Set.of(
            "RISK_ALERT", "CASE_UPDATE", "MERCHANT_STATUS_CHANGE");

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final PspIsolationService pspIsolationService;

    public WebhookSubscriptionController(WebhookSubscriptionRepository subscriptionRepository,
            PspIsolationService pspIsolationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * POST /webhooks/subscribe
     *
     * Creates a subscription scoped to the caller's own PSP — pspId is never taken from the
     * request body, only resolved from the authenticated caller, so a PSP cannot subscribe on
     * another tenant's behalf.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<WebhookSubscription> subscribe(@RequestBody SubscribeRequest request) {
        if (request == null || request.callbackUrl() == null || request.callbackUrl().isBlank()) {
            throw new IllegalArgumentException("callbackUrl is required");
        }
        if (!request.callbackUrl().startsWith("https://")) {
            throw new IllegalArgumentException("callbackUrl must use HTTPS");
        }
        if (request.eventType() == null || !VALID_EVENT_TYPES.contains(request.eventType())) {
            throw new IllegalArgumentException(
                    "eventType must be one of " + VALID_EVENT_TYPES);
        }

        Long pspId = pspIsolationService.getCurrentUserPspId();
        WebhookSubscription subscription = WebhookSubscription.builder()
                .pspId(String.valueOf(pspId))
                .callbackUrl(request.callbackUrl())
                .eventType(request.eventType())
                .secretKey(generateSecret())
                .isActive(true)
                .build();

        return ResponseEntity.ok(subscriptionRepository.save(subscription));
    }

    /**
     * GET /webhooks/subscriptions
     *
     * Lists only the caller's own PSP's subscriptions. Platform admins (pspId 0) see every
     * subscription, matching the read-scoping convention used elsewhere (TransactionMonitoringService,
     * AlertDispositionService).
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<List<WebhookSubscription>> listSubscriptions() {
        Long pspId = pspIsolationService.getCurrentUserPspId();
        List<WebhookSubscription> subscriptions = (pspId != null && pspId != 0L)
                ? subscriptionRepository.findByPspId(String.valueOf(pspId))
                : subscriptionRepository.findAll();
        return ResponseEntity.ok(subscriptions);
    }

    /**
     * DELETE /webhooks/subscriptions/{id}
     *
     * Unsubscribes — verifies the subscription belongs to the caller's own PSP before deleting,
     * the same tenant-isolation pattern as UserController.requireSamePsp (this session's earlier
     * IDOR fix): a PSP admin must not be able to delete another tenant's webhook subscription by
     * guessing a numeric id.
     */
    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long id) {
        WebhookSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        Long callerPspId = pspIsolationService.getCurrentUserPspId();
        if (callerPspId != null && callerPspId != 0L
                && !String.valueOf(callerPspId).equals(subscription.getPspId())) {
            throw new SecurityException("Cannot manage a webhook subscription from another PSP");
        }

        subscriptionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record SubscribeRequest(String callbackUrl, String eventType) {}
}
