package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Evidence Chain of Custody Entity
 * Tracks all access and modifications to evidence
 */
@Entity
@Table(name = "evidence_chain_of_custody", indexes = {
    @Index(name = "idx_custody_evidence", columnList = "evidence_id"),
    @Index(name = "idx_custody_timestamp", columnList = "timestamp")
})
public class EvidenceChainOfCustody {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id", nullable = false)
    private CaseEvidence evidence;

    @Column(name = "action", nullable = false, length = 50)
    private String action; // VIEWED, DOWNLOADED, MODIFIED, TRANSFERRED, DELETED

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CaseEvidence getEvidence() {
        return evidence;
    }

    public void setEvidence(CaseEvidence evidence) {
        this.evidence = evidence;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

