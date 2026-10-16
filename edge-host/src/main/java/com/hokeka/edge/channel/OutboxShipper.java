package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.crypto.EdgeKeys;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.LedgerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Durable delivery of decision records v2 from the Edge Ledger outbox to the Control Plane
 * (EDGE_DATA_RESIDENCY_CONTRACT.md §4.2, §4.3). Replaces the former in-memory assessment buffer:
 * records live in {@code edge_outbox}, so a restart or a Control Plane outage loses nothing.
 *
 * <h2>Protocol</h2>
 * <ul>
 *   <li>Drains this node's unshipped rows in {@code seq} order, at least once.</li>
 *   <li>Each POST carries one batch {@code {v:2, edgeId, siteId, outboxSeqFrom, outboxSeqTo,
 *       records[1..500]}}, validated against {@code decision-record-batch-v2}, wrapped in the replay
 *       envelope and HSE-1 sealed with context {@code hokeka.decision.records} to the pinned Control
 *       Plane X25519 key, signed with this node's Ed25519 key, sent as
 *       {@code application/octet-stream} to {@code POST /api/v1/edge/assessments} over mTLS. Batches
 *       are sized so the sealed body stays within the 256 KiB payload limit.</li>
 *   <li>{@code 200}: the acknowledgement is validated against {@code decision-record-ack-v2}; every
 *       row of the batch is marked {@code shipped_at}; rejected rows also get their code (they are
 *       not retried).</li>
 *   <li>{@code 401}/{@code 403}: the node is marked unauthorised and shipping stops.</li>
 *   <li>{@code 413}: the batch is halved and retried.</li>
 *   <li>Anything else (including a malformed acknowledgement): jittered exponential backoff
 *       1 s → 60 s.</li>
 * </ul>
 */
public class OutboxShipper {

    private static final Logger log = LoggerFactory.getLogger(OutboxShipper.class);

    /** Control Plane endpoint for decision records (§4.2). */
    public static final String ASSESSMENTS_PATH = "/api/v1/edge/assessments";

    /** Contract batch bound (§4.2). */
    static final int MAX_RECORDS_PER_BATCH = 500;

    static final Duration BACKOFF_MIN = Duration.ofSeconds(1);
    static final Duration BACKOFF_MAX = Duration.ofSeconds(60);

    private final ControlPlaneProperties properties;
    private final LedgerProperties ledgerProperties;
    private final SecureChannel channel;
    private final SealedEnvelopeCodec codec;
    private final ActivationService activation;
    private final EdgeLedger ledger;
    private final ContractSchemas schemas;
    private final ObjectMapper mapper;
    private final Clock clock;

    private final AtomicLong shipped = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong failedAttempts = new AtomicLong();
    private final AtomicLong localDefects = new AtomicLong();

    private volatile int consecutiveFailures;
    private volatile Instant nextAttemptAt = Instant.EPOCH;
    private volatile Instant lastShippedAt;
    private volatile int batchLimit;
    private volatile boolean missingKeyLogged;

    /**
     * @param properties       control-plane channel settings (edge id, pinned keys)
     * @param ledgerProperties ledger settings (site id, batch size, payload cap)
     * @param channel          mTLS client factory
     * @param codec            replay envelope + HSE-1 codec
     * @param activation       node identity, keys and authorisation state
     * @param ledger           the Edge Ledger holding the outbox
     * @param schemas          contract schema validator
     * @param mapper           JSON mapper
     * @param clock            time source (tests inject a fixed or stepping clock)
     */
    public OutboxShipper(ControlPlaneProperties properties, LedgerProperties ledgerProperties, SecureChannel channel,
                         SealedEnvelopeCodec codec, ActivationService activation, EdgeLedger ledger,
                         ContractSchemas schemas, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.ledgerProperties = ledgerProperties;
        this.channel = channel;
        this.codec = codec;
        this.activation = activation;
        this.ledger = ledger;
        this.schemas = schemas;
        this.mapper = mapper;
        this.clock = clock;
        this.batchLimit = clampBatch(ledgerProperties.getOutboxBatchSize());
    }

    /** What one {@link #ship()} run did. */
    public enum RunOutcome {
        /** Nothing to do, or everything pending was delivered. */
        DRAINED,
        /** Not attempted: channel disabled, node unauthorised, ledger down, keys missing or backing off. */
        SKIPPED,
        /** A delivery failed; the next attempt waits for the backoff. */
        BACKING_OFF,
        /** The Control Plane refused this node (401/403). */
        UNAUTHORIZED
    }

    /**
     * Drains the outbox until it is empty or a delivery fails. Called on a fixed delay
     * ({@code EDGE_ASSESSMENT_SHIP_INTERVAL}). Never throws.
     */
    public RunOutcome ship() {
        try {
            return shipOnce();
        } catch (RuntimeException e) {
            log.warn("Decision record shipping failed unexpectedly ({})", e.getClass().getSimpleName());
            backOff();
            return RunOutcome.BACKING_OFF;
        }
    }

    private RunOutcome shipOnce() {
        if (!properties.isEnabled() || !activation.authorized() || !ledger.ready()) {
            return RunOutcome.SKIPPED;
        }
        Instant now = clock.instant();
        if (now.isBefore(nextAttemptAt)) {
            return RunOutcome.SKIPPED;
        }
        String encryptionKey = properties.getEncryptionPublicKey();
        if (encryptionKey == null || encryptionKey.isBlank()) {
            if (!missingKeyLogged) {
                missingKeyLogged = true;
                log.error("CONTROLPLANE_X25519_PUBLIC_KEY is not set: decision records stay in the outbox "
                        + "until it is configured");
            }
            return RunOutcome.SKIPPED;
        }
        byte[] controlPlaneX25519 = EdgeKeys.decodeX25519PublicRaw(encryptionKey);
        String edgeId = activation.loadOrCreateIdentity().edgeId();
        String siteId = ledgerProperties.effectiveSiteId(edgeId);

        while (true) {
            List<EdgeLedger.OutboxRow> rows;
            try {
                rows = ledger.pendingOutbox(edgeId, batchLimit);
            } catch (Exception e) {
                log.warn("Outbox read failed ({}); retrying later", EdgeLedger.safeMessage(e));
                backOff();
                return RunOutcome.BACKING_OFF;
            }
            if (rows.isEmpty()) {
                resetBackoff();
                return RunOutcome.DRAINED;
            }
            Batch batch = buildBatch(rows, edgeId, siteId, controlPlaneX25519);
            if (batch == null) {
                continue; // every row was a local defect and has been set aside; read the next ones
            }
            RunOutcome outcome = post(batch, edgeId);
            if (outcome != null) {
                return outcome;
            }
        }
    }

    /** A sealed batch ready to POST, with the rows it covers. */
    private record Batch(byte[] sealed, List<EdgeLedger.OutboxRow> rows) {
    }

    /**
     * Turns outbox rows into a sealed batch that fits the payload cap. Rows whose stored payload no
     * longer passes the record schema are set aside as local defects (marked shipped with
     * {@code SCHEMA_INVALID}, never sent, §4.1). Returns null when nothing remained to send.
     */
    private Batch buildBatch(List<EdgeLedger.OutboxRow> rows, String edgeId, String siteId, byte[] cpKey) {
        List<EdgeLedger.OutboxRow> good = new ArrayList<>(rows.size());
        List<JsonNode> records = new ArrayList<>(rows.size());
        Map<Long, String> defects = new LinkedHashMap<>();
        for (EdgeLedger.OutboxRow row : rows) {
            JsonNode record;
            try {
                record = mapper.readTree(row.payload());
            } catch (Exception e) {
                defects.put(row.seq(), "SCHEMA_INVALID");
                continue;
            }
            if (!schemas.validate(ContractSchemas.DECISION_RECORD_V2, record).isEmpty()
                    || !edgeId.equals(record.path("edgeNodeId").asText())) {
                defects.put(row.seq(), "SCHEMA_INVALID");
                continue;
            }
            good.add(row);
            records.add(record);
        }
        if (!defects.isEmpty()) {
            setAsideDefects(defects);
        }
        if (good.isEmpty()) {
            return null;
        }
        int count = good.size();
        while (true) {
            ObjectNode payload = batchPayload(edgeId, siteId, good.subList(0, count), records.subList(0, count));
            List<String> violations = schemas.validate(ContractSchemas.DECISION_RECORD_BATCH_V2, payload);
            if (!violations.isEmpty()) {
                // The records passed individually, so this is an envelope defect (edge/site id).
                throw new IllegalStateException("decision record batch violates decision-record-batch-v2: "
                        + violations);
            }
            byte[] sealed = codec.seal(payload, edgeId, cpKey, activation.ed25519Private(),
                    SealedEnvelopeCodec.CTX_DECISION_RECORDS, clock.instant());
            if (sealed.length <= ledgerProperties.getOutboxMaxPayloadBytes() || count == 1) {
                return new Batch(sealed, List.copyOf(good.subList(0, count)));
            }
            count = Math.max(1, count / 2);
        }
    }

    /** The batch document of §4.2. */
    private ObjectNode batchPayload(String edgeId, String siteId, List<EdgeLedger.OutboxRow> rows,
                                    List<JsonNode> records) {
        ObjectNode batch = mapper.createObjectNode();
        batch.put("v", 2);
        batch.put("edgeId", edgeId);
        batch.put("siteId", siteId);
        batch.put("outboxSeqFrom", rows.get(0).seq());
        batch.put("outboxSeqTo", rows.get(rows.size() - 1).seq());
        ArrayNode list = batch.putArray("records");
        records.forEach(list::add);
        return batch;
    }

    /**
     * POSTs one batch and applies the result. Returns null to continue draining, or the outcome
     * that ends this run.
     */
    private RunOutcome post(Batch batch, String edgeId) {
        List<Long> seqs = batch.rows().stream().map(EdgeLedger.OutboxRow::seq).toList();
        ResponseEntity<String> response;
        try {
            response = channel.mutualTlsClient().post()
                    .uri(ASSESSMENTS_PATH)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Hokeka-Edge-Id", edgeId)
                    .body(batch.sealed())
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            noteFailure(seqs, "HTTP_" + status);
            if (status == 401 || status == 403) {
                activation.markUnauthorized("control plane rejected a decision record upload (HTTP " + status + ")");
                log.warn("Decision record upload rejected (HTTP {}); outbox kept", status);
                backOff();
                return RunOutcome.UNAUTHORIZED;
            }
            if (status == 413 && batch.rows().size() > 1) {
                batchLimit = Math.max(1, batch.rows().size() / 2);
                log.warn("Decision record batch of {} was too large (HTTP 413); retrying with {}",
                        batch.rows().size(), batchLimit);
                return null;
            }
            log.warn("Decision record upload failed (HTTP {}); backing off", status);
            backOff();
            return RunOutcome.BACKING_OFF;
        } catch (Exception e) {
            noteFailure(seqs, "IO_ERROR");
            log.warn("Decision record upload failed ({}); backing off", e.getClass().getSimpleName());
            backOff();
            return RunOutcome.BACKING_OFF;
        }

        Map<Long, String> rejectCodes = parseAck(response.getBody(), batch.rows());
        if (rejectCodes == null) {
            noteFailure(seqs, "ACK_INVALID");
            log.warn("Control Plane acknowledgement did not match decision-record-ack-v2 for this batch; "
                    + "backing off and re-sending");
            backOff();
            return RunOutcome.BACKING_OFF;
        }
        try {
            ledger.markShipped(seqs, rejectCodes, clock.instant());
        } catch (Exception e) {
            // Delivered but not marked: the batch is re-sent later and counted as duplicates by the
            // Control Plane (idempotent on edgeAssessmentId).
            log.warn("Delivered decision records could not be marked shipped ({}); they will be re-sent",
                    EdgeLedger.safeMessage(e));
            backOff();
            return RunOutcome.BACKING_OFF;
        }
        shipped.addAndGet(seqs.size() - rejectCodes.size());
        rejected.addAndGet(rejectCodes.size());
        if (!rejectCodes.isEmpty()) {
            log.warn("Control Plane rejected {} decision record(s): {}", rejectCodes.size(), rejectCodes.values());
        }
        lastShippedAt = clock.instant();
        batchLimit = clampBatch(ledgerProperties.getOutboxBatchSize());
        resetBackoff();
        return null;
    }

    /**
     * Validates the acknowledgement (§4.2, {@code decision-record-ack-v2}) and maps rejected ids
     * onto outbox seqs. Returns null when the body is not a valid acknowledgement of this batch
     * (schema failure, counts that do not add up, or a rejected id that was not sent).
     */
    public Map<Long, String> parseAck(String body, List<EdgeLedger.OutboxRow> rows) {
        JsonNode ack;
        try {
            ack = body == null ? null : mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
        if (ack == null || !schemas.validate(ContractSchemas.DECISION_RECORD_ACK_V2, ack).isEmpty()) {
            return null;
        }
        Map<UUID, Long> seqById = new HashMap<>();
        for (EdgeLedger.OutboxRow row : rows) {
            seqById.put(row.edgeAssessmentId(), row.seq());
        }
        Map<Long, String> rejectCodes = new LinkedHashMap<>();
        for (JsonNode r : ack.path("rejected")) {
            Long seq = seqById.get(UUID.fromString(r.path("edgeAssessmentId").asText()));
            if (seq == null) {
                return null;
            }
            rejectCodes.put(seq, r.path("code").asText());
        }
        long total = ack.path("accepted").asLong() + ack.path("duplicates").asLong() + rejectCodes.size();
        if (total != rows.size()) {
            return null;
        }
        duplicates.addAndGet(ack.path("duplicates").asLong());
        return rejectCodes;
    }

    /** Marks locally invalid rows as rejected so they never block the outbox (§4.1 "not shipped"). */
    private void setAsideDefects(Map<Long, String> defects) {
        try {
            ledger.markShipped(defects.keySet(), defects, clock.instant());
            localDefects.addAndGet(defects.size());
            log.error("{} outbox row(s) failed decision-record-v2 validation and were set aside unshipped "
                    + "(reject_code SCHEMA_INVALID)", defects.size());
        } catch (Exception e) {
            throw new IllegalStateException("could not set aside invalid outbox rows", e);
        }
    }

    /** Records a failed attempt on the rows; a failure to do so is logged, not fatal. */
    private void noteFailure(List<Long> seqs, String code) {
        failedAttempts.incrementAndGet();
        try {
            ledger.recordFailedAttempt(seqs, code, clock.instant());
        } catch (Exception e) {
            log.debug("Could not record the failed attempt ({})", EdgeLedger.safeMessage(e));
        }
    }

    /**
     * Schedules the next attempt with jittered exponential backoff: base 1 s doubling to a 60 s
     * cap, and the actual wait drawn uniformly from [base/2, base] so a fleet does not retry in step.
     */
    private void backOff() {
        int failures = Math.min(16, ++consecutiveFailures);
        long base = Math.min(BACKOFF_MAX.toMillis(), BACKOFF_MIN.toMillis() << Math.min(6, failures - 1));
        long wait = base / 2 + ThreadLocalRandom.current().nextLong(base / 2 + 1);
        nextAttemptAt = clock.instant().plusMillis(Math.max(BACKOFF_MIN.toMillis() / 2, wait));
    }

    /** Clears the backoff after a successful delivery or an empty outbox. */
    private void resetBackoff() {
        consecutiveFailures = 0;
        nextAttemptAt = Instant.EPOCH;
    }

    private static int clampBatch(int configured) {
        return Math.max(1, Math.min(MAX_RECORDS_PER_BATCH, configured));
    }

    /** Records delivered and accepted (including duplicates) since start. */
    public long shipped() {
        return shipped.get();
    }

    /** Records the Control Plane rejected since start. */
    public long rejected() {
        return rejected.get();
    }

    /** Records the Control Plane counted as duplicates since start. */
    public long duplicates() {
        return duplicates.get();
    }

    /** Failed delivery attempts since start. */
    public long failedAttempts() {
        return failedAttempts.get();
    }

    /** Outbox rows set aside locally because they failed the record schema. */
    public long localDefects() {
        return localDefects.get();
    }

    /** Earliest time of the next attempt ({@link Instant#EPOCH} when not backing off). */
    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    /** Time of the last successful delivery, or null. */
    public Instant lastShippedAt() {
        return lastShippedAt;
    }
}
