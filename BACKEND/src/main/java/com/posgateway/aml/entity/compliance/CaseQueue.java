package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.model.CasePriority;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Case Queue Entity
 * Represents a queue for case assignment
 */
@Entity
@Table(name = "case_queues")
@Audited
public class CaseQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue_name", nullable = false, unique = true)
    private String queueName;

    @Column(name = "target_role")
    private String targetRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_priority")
    private CasePriority minPriority;

    @Column(name = "max_queue_size")
    private Integer maxQueueSize;

    @Column(name = "auto_assign")
    private Boolean autoAssign = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "queue")
    private List<ComplianceCase> queuedCases;

    public CaseQueue() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public CasePriority getMinPriority() {
        return minPriority;
    }

    public void setMinPriority(CasePriority minPriority) {
        this.minPriority = minPriority;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public Boolean getAutoAssign() {
        return autoAssign;
    }

    public void setAutoAssign(Boolean autoAssign) {
        this.autoAssign = autoAssign;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ComplianceCase> getQueuedCases() {
        return queuedCases;
    }

    public void setQueuedCases(List<ComplianceCase> queuedCases) {
        this.queuedCases = queuedCases;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (enabled == null) {
            enabled = true;
        }
        if (autoAssign == null) {
            autoAssign = false;
        }
    }
}

