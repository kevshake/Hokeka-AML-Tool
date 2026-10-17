package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.edge.tunnel.TunnelTime;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import com.posgateway.aml.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives one back-fill run through contract §11, one step per call: BEGIN, each BATCH, COMMIT, then
 * the cloud verification. Every Edge acknowledgement is persisted before the next request, so the
 * run resumes at the exact step after a crash, a restart or an operator pause.
 *
 * <p><b>Failures.</b> Edge offline, busy ({@code retryAfterMs} honoured), timeout, deadline, ledger
 * unavailable and transient faults back off exponentially and keep the run RUNNING; after
 * {@code maxConsecutiveFailures} it is STALLED. {@code FRAME_TOO_LARGE} halves the batch byte budget.
 * Anything else (backfill not allowed, op not allowed, tenant mismatch, rejected phase) is FAILED with
 * the code. {@code VERIFY_MISMATCH} re-sends every batch (the Edge treats them as duplicates) up to
 * {@code maxResendRounds}, then FAILED. ABORT is sent for runs the Edge has begun.
 *
 * <p>Logs carry run ids, counts and codes only.
 */
@Component
public class BackfillEngine {

    private static final Logger log = LoggerFactory.getLogger(BackfillEngine.class);

    /** Errors after which the same request may succeed later. */
    static final Set<TunnelErrorCode> RETRYABLE = EnumSet.of(TunnelErrorCode.EDGE_OFFLINE, TunnelErrorCode.EDGE_BUSY,
            TunnelErrorCode.EDGE_TIMEOUT, TunnelErrorCode.DEADLINE_EXCEEDED, TunnelErrorCode.LEDGER_UNAVAILABLE,
            TunnelErrorCode.INTERNAL, TunnelErrorCode.CANCELLED, TunnelErrorCode.STREAM_STALLED,
            TunnelErrorCode.SEAL_INVALID, TunnelErrorCode.REPLAY_DETECTED, TunnelErrorCode.BINDING_MISMATCH);

    private final BackfillRunStore store;
    private final LegacyRowReader reader;
    private final BackfillReferenceUpdater references;
    private final BackfillVerifier verifier;
    private final BackfillTransport transport;
    private final PspEdgeSettingsRepository settings;
    private final AuditLogService audit;
    private final BackfillProperties props;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** Wires the engine. */
    @org.springframework.beans.factory.annotation.Autowired
    public BackfillEngine(BackfillRunStore store, LegacyRowReader reader, BackfillReferenceUpdater references,
                          BackfillVerifier verifier, BackfillTransport transport, PspEdgeSettingsRepository settings,
                          AuditLogService audit, BackfillProperties props, JdbcTemplate jdbc,
                          PlatformTransactionManager txManager, ObjectMapper mapper) {
        this(store, reader, references, verifier, transport, settings, audit, props, jdbc, txManager, mapper,
                Clock.systemUTC());
    }

    /** Wires the engine with an explicit clock (tests). */
    BackfillEngine(BackfillRunStore store, LegacyRowReader reader, BackfillReferenceUpdater references,
                   BackfillVerifier verifier, BackfillTransport transport, PspEdgeSettingsRepository settings,
                   AuditLogService audit, BackfillProperties props, JdbcTemplate jdbc,
                   PlatformTransactionManager txManager, ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.reader = reader;
        this.references = references;
        this.verifier = verifier;
        this.transport = transport;
        this.settings = settings;
        this.audit = audit;
        this.props = props;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Performs the run's next step.
     *
     * @return true when the run can take another step right away (no back-off, not finished)
     */
    public boolean step(BackfillRun run) {
        try {
            return switch (run.state()) {
                case ABORTING -> abort(run);
                case PENDING, RUNNING -> switch (run.phase()) {
                    case BEGIN -> begin(run);
                    case BATCH -> batch(run);
                    case COMMIT -> commit(run);
                    case VERIFY -> verify(run);
                    case DONE -> false;
                };
                default -> false;
            };
        } catch (EdgeDataException e) {
            return onEdgeError(run, e);
        } catch (DataAccessException e) {
            log.error("Back-fill {}: cloud database failure in phase {} ({})", run.backfillId(), run.phase(),
                    e.getClass().getSimpleName());
            retry(run, "CLOUD_DATABASE", null);
            return false;
        } catch (RuntimeException e) {
            // A cloud-side defect: back off (and eventually STALL) instead of hammering the Edge.
            log.error("Back-fill {}: cloud fault in phase {} ({})", run.backfillId(), run.phase(),
                    e.getClass().getSimpleName());
            retry(run, "CLOUD_FAULT", null);
            return false;
        }
    }

    // ── phases ───────────────────────────────────────────────────────────────────────────────────

    /** §11 steps 1 and 3: preconditions, then BEGIN with the run's frozen window. */
    private boolean begin(BackfillRun run) {
        if (!preconditionsHold(run)) {
            return false;
        }
        JsonNode health = transport.health(run.pspId(), run.siteId());
        if (!"OK".equals(health.path("status").asText())) {
            retry(run, "LEDGER_NOT_OK", null);
            return false;
        }
        ObjectNode params = mapper.createObjectNode();
        params.put("phase", "BEGIN");
        params.put("backfillId", run.backfillId().toString());
        params.put("sourceFrom", TunnelTime.formatMillis(run.sourceFrom()));
        params.put("sourceTo", TunnelTime.formatMillis(run.sourceTo()));
        params.put("expectedRows", run.expectedRows());
        params.put("retentionCutoff", TunnelTime.formatMillis(run.retentionCutoff()));
        JsonNode ack = transport.backfill(run.pspId(), run.siteId(), params);
        if (!"BEGIN".equals(ack.path("phase").asText()) || !"OPEN".equals(ack.path("state").asText())
                || !run.backfillId().toString().equals(ack.path("backfillId").asText())) {
            fail(run, "BEGIN_NOT_OPEN");
            return false;
        }
        store.markBegun(run.backfillId());
        log.info("Back-fill {} (PSP {}, site {}): BEGIN acknowledged, {} row(s) selected", run.backfillId(),
                run.pspId(), run.siteId(), run.expectedRows());
        return true;
    }

    /** §11 step 4: the next batch from the persisted cursor, or the next source / COMMIT when exhausted. */
    private boolean batch(BackfillRun run) {
        if (!preconditionsHold(run)) {
            return false;
        }
        LegacySource source = run.source();
        List<LegacyRow> rows = reader.next(run.backfillId(), run.retentionCutoff(), run.sourceTo(), source,
                run.position(), props.getBatchRows());
        if (rows.isEmpty()) {
            store.advanceSource(run.backfillId(), source.next());
            return true;
        }
        LegacyRowMapper.RuleNameIndex rules = ruleIndex(run.pspId());
        List<LegacyRow> taken = new ArrayList<>();
        ArrayNode sent = mapper.createArrayNode();
        long bytes = 0;
        for (LegacyRow row : rows) {
            ObjectNode mapped = LegacyRowMapper.toBackfillRow(row, rules);
            long size = size(mapped) + 1;
            if (!taken.isEmpty() && bytes + size > run.batchByteBudget()) {
                break;
            }
            taken.add(row);
            sent.add(mapped);
            bytes += size;
        }
        ObjectNode params = mapper.createObjectNode();
        params.put("phase", "BATCH");
        params.put("backfillId", run.backfillId().toString());
        params.put("batchSeq", run.nextBatchSeq());
        params.set("rows", sent);
        JsonNode ack = transport.backfill(run.pspId(), run.siteId(), params);

        Map<String, String> rejected = new HashMap<>();
        ack.path("rejected").forEach(r -> rejected.put(r.path("legacyTransactionId").asText(), r.path("code").asText()));
        int accepted = ack.path("accepted").asInt(-1);
        int duplicates = ack.path("duplicates").asInt(-1);
        Set<String> sentIds = new HashSet<>();
        taken.forEach(r -> sentIds.add(r.legacyTransactionId()));
        boolean consistent = "BATCH".equals(ack.path("phase").asText())
                && ack.path("batchSeq").asLong(-1) == run.nextBatchSeq()
                && accepted >= 0 && duplicates >= 0 && accepted + duplicates + rejected.size() == taken.size()
                && sentIds.containsAll(rejected.keySet());
        if (!consistent) {
            fail(run, "ACK_INVALID");
            return false;
        }
        List<BackfillRunStore.RowOutcome> outcomes = new ArrayList<>(taken.size());
        List<BackfillReferenceUpdater.HeldRow> held = new ArrayList<>();
        for (int i = 0; i < taken.size(); i++) {
            LegacyRow row = taken.get(i);
            String code = rejected.get(row.legacyTransactionId());
            outcomes.add(new BackfillRunStore.RowOutcome(row.source(), row.sourceId(), row.legacyTransactionId(),
                    row.edgeAssessmentId(), row.minted(), code));
            if (code == null) {
                held.add(new BackfillReferenceUpdater.HeldRow(row, (ObjectNode) sent.get(i)));
            }
        }
        LegacyRow last = taken.get(taken.size() - 1);
        tx.executeWithoutResult(status -> {
            store.recordBatch(run, run.nextBatchSeq(), outcomes, accepted, duplicates, source,
                    LegacyRowReader.positionOf(last));
            references.apply(run.pspId(), run.siteId(), held);
        });
        log.info("Back-fill {} BATCH {}: {} row(s), {} accepted, {} duplicate(s), {} rejected", run.backfillId(),
                run.nextBatchSeq(), taken.size(), accepted, duplicates, rejected.size());
        return true;
    }

    /** §11 step 5: COMMIT with the count and digest of the rows the Edge acknowledged holding. */
    private boolean commit(BackfillRun run) {
        BackfillRunStore.HeldDigest held = store.heldDigest(run.backfillId());
        ObjectNode params = mapper.createObjectNode();
        params.put("phase", "COMMIT");
        params.put("backfillId", run.backfillId().toString());
        params.put("expectedRows", held.rows());
        params.put("expectedIdDigest", held.digest());
        JsonNode ack = transport.backfill(run.pspId(), run.siteId(), params);
        String state = ack.path("state").asText();
        long edgeRows = ack.path("rowsTotal").asLong(-1);
        String edgeDigest = ack.path("idDigest").asText(null);
        boolean verified = "VERIFIED".equals(state) && edgeRows == held.rows() && held.digest().equals(edgeDigest);
        store.recordCommit(run.backfillId(), held, verified ? "VERIFIED" : "VERIFY_MISMATCH", Math.max(0, edgeRows),
                edgeDigest);
        if (verified) {
            log.info("Back-fill {} COMMIT verified: {} row(s), digests equal", run.backfillId(), held.rows());
            return true;
        }
        if (run.resendRound() < props.getMaxResendRounds()) {
            log.warn("Back-fill {} COMMIT mismatch (Edge holds {}, cloud expects {}): re-sending all batches, round {}",
                    run.backfillId(), edgeRows, held.rows(), run.resendRound() + 1);
            store.restartRound(run.backfillId());
            return true;
        }
        fail(run, "VERIFY_MISMATCH");
        return false;
    }

    /** Cloud verification: content sample and rejection triage; VERIFIED only when the sample matched. */
    private boolean verify(BackfillRun run) {
        BackfillVerifier.Result result = verifier.verify(run, transport, ruleIndex(run.pspId()));
        store.recordVerification(run.backfillId(), result.sampleSize(), result.mismatches(), result.matched());
        Map<String, Object> summary = summary(run);
        summary.put("sampleSize", result.sampleSize());
        summary.put("sampleMismatches", result.mismatches());
        summary.put("rejectionsResolvedAutomatically", result.resolvedRejections());
        if (result.matched()) {
            log.info("Back-fill {} VERIFIED ({} sampled row(s) match)", run.backfillId(), result.sampleSize());
            audit.logAction(null, "EDGE_BACKFILL_VERIFIED", "EDGE_BACKFILL_RUN", run.backfillId().toString(), null,
                    summary, null, "Back-fill verified: COMMIT digest and content sample match");
        } else {
            audit.logAction(null, "EDGE_BACKFILL_FAILED", "EDGE_BACKFILL_RUN", run.backfillId().toString(), null,
                    summary, null, "Back-fill content sample did not match the Edge");
        }
        return false;
    }

    /** Sends ABORT for a run the Edge has begun; a run never begun is simply ABORTED. */
    private boolean abort(BackfillRun run) {
        if (run.begunAt() != null) {
            ObjectNode params = mapper.createObjectNode();
            params.put("phase", "ABORT");
            params.put("backfillId", run.backfillId().toString());
            params.put("reason", run.abortReason() == null ? "OPERATOR" : run.abortReason());
            try {
                transport.backfill(run.pspId(), run.siteId(), params);
            } catch (EdgeDataException e) {
                TunnelErrorCode code = e.error().code();
                boolean keepTrying = RETRYABLE.contains(code)
                        && run.consecutiveFailures() + 1 < props.getMaxConsecutiveFailures();
                if (keepTrying) {
                    throw e;
                }
                // BACKFILL_REJECTED: the Edge holds no open run under this id. Anything else that can
                // never succeed (or retries exhausted): the Edge run stays OPEN, which is harmless
                // because it is insert-only and a new run uses a new backfillId.
                log.info("Back-fill {} ABORT not acknowledged by the Edge ({}); closed in the cloud", run.backfillId(),
                        code);
            }
        }
        store.markAborted(run.backfillId());
        audit.logAction(null, "EDGE_BACKFILL_ABORTED", "EDGE_BACKFILL_RUN", run.backfillId().toString(), null,
                summary(run), null, "Back-fill aborted; rows already on the Edge stay (ledger.backfill never deletes)");
        log.info("Back-fill {} ABORTED", run.backfillId());
        return false;
    }

    // ── preconditions and failures ───────────────────────────────────────────────────────────────

    /**
     * §11 step 1, re-checked before BEGIN and every BATCH: the PSP's tunnel is on, back-fill allowed
     * and {@code ledger.backfill} among the ops the cloud may send. Withdrawn consent fails the run.
     */
    private boolean preconditionsHold(BackfillRun run) {
        PspEdgeSettings s = settings.findById(run.pspId()).orElseGet(() -> PspEdgeSettings.defaultsFor(run.pspId()));
        if (!s.isTunnelEnabled() || !s.isTunnelBackfillAllowed()
                || !s.getTunnelOpsAllowed().contains("ledger.backfill")) {
            fail(run, TunnelErrorCode.BACKFILL_NOT_ALLOWED.name());
            return false;
        }
        return true;
    }

    /** Maps an Edge / tunnel error onto retry, budget shrink or failure. */
    private boolean onEdgeError(BackfillRun run, EdgeDataException e) {
        TunnelErrorCode code = e.error().code();
        if (code == TunnelErrorCode.FRAME_TOO_LARGE) {
            int budget = store.halveBudget(run.backfillId());
            if (budget < props.getMinBatchBytes()) {
                fail(run, code.name());
                return false;
            }
            log.warn("Back-fill {}: frame too large, batch byte budget now {}", run.backfillId(), budget);
            return true;
        }
        if (RETRYABLE.contains(code)) {
            retry(run, code.name(), e.error().retryAfterMs());
            return false;
        }
        fail(run, code.name());
        return false;
    }

    /** Backs off; STALLED after too many consecutive failures. */
    private void retry(BackfillRun run, String code, Long retryAfterMs) {
        long delay = Math.max(retryAfterMs == null ? 0 : retryAfterMs, props.backoffMs(run.consecutiveFailures() + 1));
        Instant next = clock.instant().plusMillis(delay);
        BackfillState after = store.recordRetry(run.backfillId(), code, next, props.getMaxConsecutiveFailures());
        log.warn("Back-fill {} phase {}: {} (attempt {}), next try in {} ms", run.backfillId(), run.phase(), code,
                run.consecutiveFailures() + 1, delay);
        if (after == BackfillState.STALLED) {
            audit.logAction(null, "EDGE_BACKFILL_STALLED", "EDGE_BACKFILL_RUN", run.backfillId().toString(), null,
                    summary(run), null, "Back-fill stalled after repeated Edge failures: " + code);
        }
    }

    /** Fails the run with a code and audits it. */
    private void fail(BackfillRun run, String code) {
        store.recordFailure(run.backfillId(), code);
        log.warn("Back-fill {} FAILED in phase {}: {}", run.backfillId(), run.phase(), code);
        Map<String, Object> summary = summary(run);
        summary.put("code", code);
        audit.logAction(null, "EDGE_BACKFILL_FAILED", "EDGE_BACKFILL_RUN", run.backfillId().toString(), null,
                summary, null, "Back-fill failed: " + code);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Rule names → ids for the PSP: its own rule definitions first, then platform (global) ones; the
     * lowest id wins among equal names.
     */
    LegacyRowMapper.RuleNameIndex ruleIndex(long pspId) {
        Map<String, Long> byName = new LinkedHashMap<>();
        jdbc.query("SELECT id, name FROM rule_definitions WHERE psp_id = ? OR psp_id IS NULL "
                + "ORDER BY (psp_id IS NULL), id", rs -> {
            byName.putIfAbsent(rs.getString("name"), rs.getLong("id"));
        }, pspId);
        return LegacyRowMapper.RuleNameIndex.of(byName);
    }

    /** Serialized size of a mapped row. */
    private long size(ObjectNode row) {
        try {
            return mapper.writeValueAsBytes(row).length;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a back-fill row could not be serialised", e);
        }
    }

    /** Ids and counts of a run for audit rows (no transaction values). */
    static Map<String, Object> summary(BackfillRun run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("backfillId", run.backfillId().toString());
        m.put("pspId", run.pspId());
        m.put("siteId", run.siteId());
        m.put("expectedRows", run.expectedRows());
        m.put("rowsAccepted", run.rowsAccepted());
        m.put("rowsDuplicate", run.rowsDuplicate());
        m.put("phase", run.phase().name());
        return m;
    }
}
