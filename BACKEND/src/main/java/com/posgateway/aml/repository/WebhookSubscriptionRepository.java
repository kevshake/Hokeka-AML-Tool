package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findByEventTypeAndIsActiveTrue(String eventType);

    /**
     * Tenant-scoped lookup. An event concerning one PSP must only ever be delivered to that PSP's
     * own subscriptions — the unscoped variant above would fan a tenant's event out to every other
     * tenant's callback URL, which is a cross-tenant data leak.
     */
    List<WebhookSubscription> findByPspIdAndEventTypeAndIsActiveTrue(String pspId, String eventType);

    /** All of one PSP's subscriptions regardless of event type/active status — used by the
     * self-service list endpoint (W36-2). */
    List<WebhookSubscription> findByPspId(String pspId);
}
