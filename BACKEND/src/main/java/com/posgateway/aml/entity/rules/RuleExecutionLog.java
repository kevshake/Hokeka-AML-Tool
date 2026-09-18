package com.posgateway.aml.entity.rules;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.Instant;

/**
 * Append-only execution log for the AML rules engine.
 *
 * <p>One row per (rule, transaction) evaluation. {@link #disposition} is
 * back-filled when the resulting alert is dispositioned by an investigator
 * (TRUE_POSITIVE / FALSE_POSITIVE), allowing the rules controller to compute
 * effectiveness/false-positive rates from real operator feedback.
 *
 * <p>Audited via Hibernate Envers (rule_execution_logs_aud).
 */
@Entity
@Table(name = "rule_execution_logs", indexes = {
        @Index(name = "idx_rule_exec_rule_executed",   columnList = "rule_id, executed_at"),
        @Index(name = "idx_rule_exec_rule_disposition", columnList = "rule_id, disposition"),
        @Index(name = "idx_rule_exec_psp_executed",    columnList = "psp_id, executed_at"),
        @Index(name = "idx_rule_exec_txn",             columnList = "txn_id")
})
@Audited
public class RuleExecutionLog {

    public enum Result {
        MATCH,
        NO_MATCH,
        ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "txn_id", length = 64)
    private String txnId;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "execution_time_micros")
    private Long executionTimeMicros;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private Result result;

    /**
     * Set when the alert produced by the matching rule is dispositioned.
     * Values: TRUE_POSITIVE | FALSE_POSITIVE | UNKNOWN. Stored as VARCHAR
     * (rather than another enum) so we can mirror AlertDisposition's
     * downgrades without a follow-up migration.
     */
    @Column(name = "disposition", length = 32)
    private String disposition;

    public RuleExecutionLog() {}

    public RuleExecutionLog(Long ruleId, Long pspId, String txnId,
                            Instant executedAt, Long executionTimeMicros, Result result) {
        this.ruleId = ruleId;
        this.pspId = pspId;
        this.txnId = txnId;
        this.executedAt = executedAt;
        this.executionTimeMicros = executionTimeMicros;
        this.result = result;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public Long getExecutionTimeMicros() { return executionTimeMicros; }
    public void setExecutionTimeMicros(Long executionTimeMicros) { this.executionTimeMicros = executionTimeMicros; }
    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }
}
