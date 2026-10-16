package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.ContractSchemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records every {@code /edge/evaluate} outcome on the Edge Ledger and decides what the PSP is told
 * when that fails (EDGE_DATA_RESIDENCY_CONTRACT.md §4.1, §4.3, invariant I8).
 *
 * <ol>
 *   <li>The decision record v2 is built from the row and validated against the closed
 *       {@code decision-record-v2} schema. A record that fails is a defect: it is not shipped, the
 *       evaluation row is still stored, and {@link #writeFailures()} is incremented.</li>
 *   <li>The evaluation and its outbox row are written in one transaction ({@link EdgeLedger#write}).</li>
 *   <li>If that transaction fails and {@code ledger.fail-closed=true} (default), the answer becomes
 *       {@code HOLD / LEDGER_UNAVAILABLE}; that HOLD row is kept in a bounded in-memory buffer and
 *       retried until the ledger is writable ({@link #drainRetries()}). With
 *       {@code fail-closed=false} the evaluated decision stands and is buffered the same way.</li>
 * </ol>
 */
public class EvaluationRecorder {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRecorder.class);

    /** Reason returned to the PSP on a ledger fail-closed HOLD. */
    public static final String LEDGER_HOLD_REASON =
            "edge ledger unavailable (fail-closed): decision withheld until it can be recorded";

    private final EdgeLedger ledger;
    private final ContractSchemas schemas;
    private final ObjectMapper mapper;
    private final boolean failClosed;
    private final int retryCapacity;

    private final Deque<LedgerEvaluation> retries = new ArrayDeque<>();
    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicLong retryDropped = new AtomicLong();
    private final AtomicLong recorded = new AtomicLong();

    /**
     * @param ledger  the site ledger
     * @param schemas contract schema validator
     * @param mapper  JSON mapper used to build decision records
     */
    public EvaluationRecorder(EdgeLedger ledger, ContractSchemas schemas, ObjectMapper mapper) {
        this.ledger = ledger;
        this.schemas = schemas;
        this.mapper = mapper;
        this.failClosed = ledger.properties().isFailClosed();
        this.retryCapacity = Math.max(1, ledger.properties().getRetryBufferSize());
    }

    /**
     * Records one evaluation and returns the evaluation that stands — {@code ev} itself, or its
     * {@code HOLD / LEDGER_UNAVAILABLE} replacement when the write failed under fail-closed.
     * Never throws.
     *
     * @param ev the evaluated row
     */
    public LedgerEvaluation record(LedgerEvaluation ev) {
        ObjectNode decisionRecord = validRecordOrNull(ev);
        try {
            ledger.write(ev, decisionRecord);
            recorded.incrementAndGet();
            return ev;
        } catch (EdgeLedger.LedgerWriteException e) {
            writeFailures.incrementAndGet();
            LedgerEvaluation standing = failClosed ? ev.asLedgerUnavailableHold(LEDGER_HOLD_REASON) : ev;
            log.warn("Ledger write failed for evaluation {} ({}); answering {} and retrying in memory",
                    ev.edgeAssessmentId(), e.getMessage(), standing.decision());
            enqueueRetry(standing);
            return standing;
        }
    }

    /**
     * The decision record for {@code ev} if it passes the contract schema, else null (and the
     * defect is counted and logged by location only — never by value).
     */
    ObjectNode validRecordOrNull(LedgerEvaluation ev) {
        ObjectNode record = LedgerEvaluations.decisionRecord(ev, mapper);
        List<String> violations = schemas.validate(ContractSchemas.DECISION_RECORD_V2, record);
        if (violations.isEmpty()) {
            return record;
        }
        writeFailures.incrementAndGet();
        log.error("Decision record for evaluation {} violates decision-record-v2 ({}); it is stored on the "
                + "ledger but will not be shipped", ev.edgeAssessmentId(), violations);
        return null;
    }

    /** Adds a row to the bounded retry buffer, dropping (and counting) the oldest when full. */
    private void enqueueRetry(LedgerEvaluation ev) {
        synchronized (retries) {
            while (retries.size() >= retryCapacity) {
                LedgerEvaluation dropped = retries.pollFirst();
                retryDropped.incrementAndGet();
                log.error("Ledger retry buffer full ({}): evaluation {} could not be recorded and is lost",
                        retryCapacity, dropped == null ? "?" : dropped.edgeAssessmentId());
            }
            retries.addLast(ev);
        }
    }

    /**
     * Writes buffered rows in arrival order until the buffer is empty or a write fails. A row whose
     * first attempt did commit (the client only saw an error) is reconciled to the decision the PSP
     * was answered with ({@link EdgeLedger#reconcile}). Never throws.
     *
     * @return number of rows written or reconciled in this run
     */
    public int drainRetries() {
        int done = 0;
        while (ledger.ready()) {
            LedgerEvaluation head;
            synchronized (retries) {
                head = retries.peekFirst();
            }
            if (head == null) {
                break;
            }
            ObjectNode decisionRecord = validRecordOrNull(head);
            try {
                ledger.write(head, decisionRecord);
            } catch (EdgeLedger.LedgerWriteException e) {
                if (!e.duplicate()) {
                    log.debug("Ledger still unavailable for retries ({})", e.getMessage());
                    break;
                }
                try {
                    if (!ledger.reconcile(head, decisionRecord) && decisionRecord != null) {
                        log.error("Evaluation {} was recorded and shipped before its write was confirmed; "
                                + "the Control Plane keeps the first decision record", head.edgeAssessmentId());
                    }
                } catch (EdgeLedger.LedgerWriteException again) {
                    log.debug("Ledger reconcile failed ({})", again.getMessage());
                    break;
                }
            }
            synchronized (retries) {
                if (retries.peekFirst() == head) {
                    retries.pollFirst();
                }
            }
            recorded.incrementAndGet();
            done++;
        }
        return done;
    }

    /** Ledger writes that did not commit, plus decision records that failed the schema. */
    public long writeFailures() {
        return writeFailures.get();
    }

    /** Rows waiting in the retry buffer. */
    public int retryBacklog() {
        synchronized (retries) {
            return retries.size();
        }
    }

    /** Rows lost because the retry buffer overflowed. */
    public long retryDropped() {
        return retryDropped.get();
    }

    /** Rows successfully recorded (first attempt or retry). */
    public long recorded() {
        return recorded.get();
    }

    /** Whether a failed write turns the decision into HOLD. */
    public boolean failClosed() {
        return failClosed;
    }

    /** The ledger behind this recorder. */
    public EdgeLedger ledger() {
        return ledger;
    }
}
