-- ============================================================
-- Rule Execution Logs (HOK-Rule-Effectiveness)
-- Migration: V119__rule_execution_logs.sql
-- Description: Append-only log of every (rule, transaction)
--              evaluation. Powers GET /rules/{id}/effectiveness
--              (totalExecutions, truePositives, falsePositives,
--              falsePositiveRate, averageExecutionTime, lastExecuted).
-- ============================================================

CREATE TABLE IF NOT EXISTS rule_execution_logs (
    id                    BIGSERIAL PRIMARY KEY,
    rule_id               BIGINT       NOT NULL,
    psp_id                BIGINT,
    txn_id                VARCHAR(64),
    executed_at           TIMESTAMP    NOT NULL,
    execution_time_micros BIGINT,
    result                VARCHAR(16)  NOT NULL,
    disposition           VARCHAR(32)
);

-- Hot-path index: SELECT MAX(executed_at), AVG(execution_time_micros) WHERE rule_id = ?
CREATE INDEX IF NOT EXISTS idx_rule_exec_rule_executed
    ON rule_execution_logs (rule_id, executed_at DESC);

-- Effectiveness aggregation: COUNT WHERE rule_id = ? AND disposition = ?
CREATE INDEX IF NOT EXISTS idx_rule_exec_rule_disposition
    ON rule_execution_logs (rule_id, disposition);

-- PSP-scoped reads (effectiveness per PSP, future)
CREATE INDEX IF NOT EXISTS idx_rule_exec_psp_executed
    ON rule_execution_logs (psp_id, executed_at DESC);

-- Disposition back-fill on alert resolution: UPDATE ... WHERE txn_id = ? AND result = 'MATCH'
CREATE INDEX IF NOT EXISTS idx_rule_exec_txn
    ON rule_execution_logs (txn_id);

COMMENT ON TABLE  rule_execution_logs                       IS 'Append-only log of rule engine evaluations; powers /rules/{id}/effectiveness';
COMMENT ON COLUMN rule_execution_logs.rule_id               IS 'FK -> rule_definitions.id';
COMMENT ON COLUMN rule_execution_logs.psp_id                IS 'PSP scope of the transaction (NULL for platform-level evaluations)';
COMMENT ON COLUMN rule_execution_logs.txn_id                IS 'Originating transaction id (string to mirror upstream representations)';
COMMENT ON COLUMN rule_execution_logs.execution_time_micros IS 'Wall-clock time (microseconds) spent evaluating the predicate';
COMMENT ON COLUMN rule_execution_logs.result                IS 'MATCH | NO_MATCH | ERROR';
COMMENT ON COLUMN rule_execution_logs.disposition           IS 'Back-filled when alert is dispositioned: TRUE_POSITIVE | FALSE_POSITIVE | UNKNOWN';

-- ============================================================
-- Envers audit mirror (rule_execution_logs_aud)
-- ============================================================
CREATE TABLE IF NOT EXISTS rule_execution_logs_aud (
    id                    BIGINT       NOT NULL,
    rev                   INTEGER      NOT NULL,
    revtype               SMALLINT,
    rule_id               BIGINT,
    psp_id                BIGINT,
    txn_id                VARCHAR(64),
    executed_at           TIMESTAMP,
    execution_time_micros BIGINT,
    result                VARCHAR(16),
    disposition           VARCHAR(32),
    PRIMARY KEY (id, rev)
);

CREATE INDEX IF NOT EXISTS idx_rule_execution_logs_aud_rev
    ON rule_execution_logs_aud (rev);
