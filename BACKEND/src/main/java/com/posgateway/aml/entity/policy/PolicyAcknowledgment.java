package com.posgateway.aml.entity.policy;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Policy Acknowledgment Entity
 * Tracks user acknowledgments of policy documents
 */
@Entity
@Table(name = "policy_acknowledgments", indexes = {
    @Index(name = "idx_ack_policy_user", columnList = "policy_id,user_id"),
    @Index(name = "idx_ack_user", columnList = "user_id")
})
public class PolicyAcknowledgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private AmlPolicy policy;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "acknowledged_at", nullable = false, updatable = false)
    private LocalDateTime acknowledgedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @PrePersist
    protected void onCreate() {
        this.acknowledgedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AmlPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(AmlPolicy policy) {
        this.policy = policy;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}

