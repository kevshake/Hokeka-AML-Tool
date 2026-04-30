package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findByEventTypeAndIsActiveTrue(String eventType);
}
