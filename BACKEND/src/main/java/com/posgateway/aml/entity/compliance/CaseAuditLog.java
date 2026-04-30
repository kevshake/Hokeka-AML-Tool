package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Immutable Audit Log for Case Actions.
 * Records "Who did What, When, and Why" for regulatory replay.
 */
@Entity
@Table(name = "case_audit_logs")
public class CaseAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(nullable = false)
    private String action; // e.g., "APPROVE", "ADD_NOTE", "VIEW"

    @Column(nullable = false)
    private String details; // JSON or text description

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // Who performed the action

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "previous_state")
    private String previousState; // Snapshot of relevant state before action

    @Column(name = "new_state")
    private String newState; // Snapshot of relevant state after action

    public CaseAuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public CaseAuditLog(Long caseId, String action, String details, User user, String previousState, String newState) {
        this.caseId = caseId;
        this.action = action;
        this.details = details;
        this.user = user;
        this.previousState = previousState;
        this.newState = newState;
        this.timestamp = LocalDateTime.now();
    }

    // Getters only (Immutability enforced by lack of Setters effectively, though
    // JPA can use reflection)

    public Long getId() {
        return id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }
}
