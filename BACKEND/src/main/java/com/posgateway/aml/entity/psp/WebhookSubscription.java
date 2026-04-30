package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private String pspId;

    @Column(name = "callback_url", nullable = false, length = 1000)
    private String callbackUrl;

    @Column(name = "event_type", nullable = false)
    private String eventType; // RISK_ALERT, CASE_UPDATE, MERCHANT_STATUS_CHANGE

    @Column(name = "secret_key")
    private String secretKey; // For HMAC signature

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "failure_count")
    private int failureCount = 0;

    public WebhookSubscription() {
    }

    public WebhookSubscription(Long id, String pspId, String callbackUrl, String eventType, String secretKey,
            boolean isActive, LocalDateTime createdAt, int failureCount) {
        this.id = id;
        this.pspId = pspId;
        this.callbackUrl = callbackUrl;
        this.eventType = eventType;
        this.secretKey = secretKey;
        this.isActive = isActive;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.failureCount = failureCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPspId() {
        return pspId;
    }

    public void setPspId(String pspId) {
        this.pspId = pspId;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public static WebhookSubscriptionBuilder builder() {
        return new WebhookSubscriptionBuilder();
    }

    public static class WebhookSubscriptionBuilder {
        private Long id;
        private String pspId;
        private String callbackUrl;
        private String eventType;
        private String secretKey;
        private boolean isActive = true;
        private LocalDateTime createdAt = LocalDateTime.now();
        private int failureCount = 0;

        WebhookSubscriptionBuilder() {
        }

        public WebhookSubscriptionBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public WebhookSubscriptionBuilder pspId(String pspId) {
            this.pspId = pspId;
            return this;
        }

        public WebhookSubscriptionBuilder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public WebhookSubscriptionBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public WebhookSubscriptionBuilder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public WebhookSubscriptionBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public WebhookSubscriptionBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public WebhookSubscriptionBuilder failureCount(int failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public WebhookSubscription build() {
            return new WebhookSubscription(id, pspId, callbackUrl, eventType, secretKey, isActive, createdAt,
                    failureCount);
        }

        public String toString() {
            return "WebhookSubscription.WebhookSubscriptionBuilder(id=" + this.id + ", pspId=" + this.pspId
                    + ", callbackUrl=" + this.callbackUrl + ", eventType=" + this.eventType + ", secretKey="
                    + this.secretKey + ", isActive=" + this.isActive + ", createdAt=" + this.createdAt
                    + ", failureCount=" + this.failureCount + ")";
        }
    }
}
