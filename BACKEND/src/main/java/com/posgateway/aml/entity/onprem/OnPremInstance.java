package com.posgateway.aml.entity.onprem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.envers.Audited;

import java.time.Instant;

/**
 * Registry row for one PSP on-prem AML service instance authorised by Hokeka central servers.
 *
 * <p>Client secrets are stored only as BCrypt hashes. Lease timing ({@link #leaseUntil},
 * {@link #nextCheckAt}) is assigned by the central server on each successful auth/renewal.
 */
@Entity
@Table(name = "onprem_instances",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_onprem_instances_instance_id", columnNames = {"instance_id"}),
                @UniqueConstraint(name = "uq_onprem_instances_client_id", columnNames = {"client_id"})
        },
        indexes = {
                @Index(name = "idx_onprem_instances_psp_id", columnList = "psp_id"),
                @Index(name = "idx_onprem_instances_status", columnList = "status"),
                @Index(name = "idx_onprem_instances_next_check", columnList = "next_check_at"),
                @Index(name = "idx_onprem_instances_lease_until", columnList = "lease_until")
        })
@Audited
public class OnPremInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "instance_id", nullable = false, length = 128)
    private String instanceId;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false, length = 255)
    private String clientSecretHash;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OnPremInstanceStatus status = OnPremInstanceStatus.ACTIVE;

    @Column(name = "approved_days", nullable = false)
    private Integer approvedDays = 7;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "next_check_at")
    private Instant nextCheckAt;

    @Column(name = "last_lease_jti", length = 64)
    private String lastLeaseJti;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "hostname", length = 255)
    private String hostname;

    @Column(name = "agent_version", length = 64)
    private String agentVersion;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 128)
    private String revokedBy;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = OnPremInstanceStatus.ACTIVE;
        }
        if (approvedDays == null) {
            approvedDays = 7;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean canReceiveLease() {
        return status == OnPremInstanceStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public void setClientSecretHash(String clientSecretHash) {
        this.clientSecretHash = clientSecretHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OnPremInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(OnPremInstanceStatus status) {
        this.status = status;
    }

    public Integer getApprovedDays() {
        return approvedDays;
    }

    public void setApprovedDays(Integer approvedDays) {
        this.approvedDays = approvedDays;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Instant leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }

    public void setNextCheckAt(Instant nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
    }

    public String getLastLeaseJti() {
        return lastLeaseJti;
    }

    public void setLastLeaseJti(String lastLeaseJti) {
        this.lastLeaseJti = lastLeaseJti;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
