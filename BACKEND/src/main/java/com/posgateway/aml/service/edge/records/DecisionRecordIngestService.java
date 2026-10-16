package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.service.residency.CanonicalJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Processes one sealed-and-opened decision record batch v2 from an authorised Edge node
 * (contract 4.2) and builds the acknowledgement of {@code decision-record-ack-v2.schema.json}.
 *
 * <p>Per record, in order: schema / privacy validation ({@code SCHEMA_INVALID},
 * {@code FORBIDDEN_FIELD}), node identity ({@code NODE_MISMATCH}: batch edgeId and every record's
 * edgeNodeId must equal the certificate CN), site ({@code UNKNOWN_SITE}: batch and record siteId must
 * equal the node's registered site), then storage, which is idempotent on edgeAssessmentId
 * (duplicate) and keeps the first record on any difference ({@code CONFLICT}). Each record commits
 * on its own, so a rejected or conflicting record never blocks the rest of the batch.
 */
@Service
public class DecisionRecordIngestService {

    private static final Logger log = LoggerFactory.getLogger(DecisionRecordIngestService.class);

    /** Contract 3.2 — HSE-1 context of a decision record batch. */
    public static final String DECISION_RECORDS_CONTEXT = "hokeka.decision.records";

    private final DecisionRecordStore store;

    public DecisionRecordIngestService(DecisionRecordStore store) {
        this.store = store;
    }

    /** The batch is not a decision record batch v2 (HTTP 400; nothing was stored). */
    public static class InvalidBatchException extends RuntimeException {
        public InvalidBatchException(String message) {
            super(message);
        }
    }

    /** One rejected record of the acknowledgement. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rejected(String edgeAssessmentId, String code, String message) {}

    /** The acknowledgement: accepted + duplicates + rejected.size() == records in the batch. */
    public record Ack(int accepted, int duplicates, List<Rejected> rejected) {}

    /**
     * Ingests a v2 batch for {@code node}.
     *
     * @throws InvalidBatchException when the wrapper is not a v2 batch, or a record has no usable
     *         edgeAssessmentId (it could not be acknowledged)
     */
    public Ack ingest(EdgeNode node, JsonNode batch) {
        Optional<String> wrapperProblem = DecisionRecordV2Validator.validateBatch(batch);
        if (wrapperProblem.isPresent()) {
            throw new InvalidBatchException(wrapperProblem.get());
        }
        JsonNode records = batch.get("records");
        for (int i = 0; i < records.size(); i++) {
            JsonNode id = records.get(i).path("edgeAssessmentId");
            if (!id.isTextual() || !DecisionRecordV2Validator.UUID.matcher(id.asText()).matches()) {
                throw new InvalidBatchException("record " + i + " has no lowercase UUID edgeAssessmentId");
            }
        }
        boolean batchNodeMismatch = !node.getEdgeId().equals(batch.get("edgeId").asText());
        String site = node.effectiveSiteId();
        boolean batchSiteMismatch = !site.equals(batch.get("siteId").asText());

        int accepted = 0;
        int duplicates = 0;
        List<Rejected> rejected = new ArrayList<>();
        for (JsonNode record : records) {
            String id = record.get("edgeAssessmentId").asText();
            Optional<DecisionRecordV2Validator.Violation> violation = DecisionRecordV2Validator.validateRecord(record);
            if (violation.isPresent()) {
                rejected.add(new Rejected(id, violation.get().code().name(), truncate(violation.get().message())));
                continue;
            }
            if (batchNodeMismatch || !node.getEdgeId().equals(record.get("edgeNodeId").asText())) {
                rejected.add(new Rejected(id, "NODE_MISMATCH", "edgeNodeId does not match the client certificate"));
                continue;
            }
            if (batchSiteMismatch || !site.equals(record.get("siteId").asText())) {
                rejected.add(new Rejected(id, "UNKNOWN_SITE", "siteId is not the node's registered Edge Site"));
                continue;
            }
            DecisionRecordStore.Outcome outcome = storeOne(node, record);
            switch (outcome) {
                case ACCEPTED -> accepted++;
                case DUPLICATE -> duplicates++;
                case CONFLICT -> rejected.add(new Rejected(id, "CONFLICT",
                        "edgeAssessmentId already recorded with different content"));
            }
        }
        log.info("Decision record batch from edge {} (psp {}): {} accepted, {} duplicates, {} rejected",
                node.getEdgeId(), node.getPspId(), accepted, duplicates, rejected.size());
        return new Ack(accepted, duplicates, rejected);
    }

    /** Stores one record; a lost insert race is re-classified in a fresh transaction. */
    private DecisionRecordStore.Outcome storeOne(EdgeNode node, JsonNode record) {
        String digest = CanonicalJson.sha256Hex(record);
        try {
            return store.store(node, record, digest);
        } catch (DataIntegrityViolationException race) {
            DecisionRecordStore.Outcome outcome = store.classifyExisting(node, record, digest);
            if (outcome == null) {
                throw race;
            }
            return outcome;
        }
    }

    /** Ack messages are capped at 256 characters by the schema. */
    private static String truncate(String message) {
        return message.length() <= 256 ? message : message.substring(0, 256);
    }
}
