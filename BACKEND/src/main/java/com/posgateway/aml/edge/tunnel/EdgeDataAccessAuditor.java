package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.edge.EdgeDataAccessLog;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;

import java.time.Clock;
import java.util.Collection;
import java.util.UUID;

/**
 * Writes the cloud {@code edge_data_access_log} row of every tunnel request (§7.15): inserted
 * before the request is sent (outcome {@code null}), completed on the terminal frame with the
 * sites contacted/answered, item count, bytes and outcome.
 *
 * <p>{@link #summarize} is the only path from request params into the row and keeps exactly the
 * §7.15 allow-list: time range, {@code merchantId}, {@code decisions}, {@code ruleId},
 * {@code minScore}, flags, id <b>counts</b>, link types, depth/window, filing kind and backfill
 * phase/sequence/row count. {@code txnRef} values, backfill rows and response items never reach it.
 */
public class EdgeDataAccessAuditor {

    private final EdgeDataAccessLogRepository repository;
    private final Clock clock;

    /** Creates the auditor over the JPA repository. */
    public EdgeDataAccessAuditor(EdgeDataAccessLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Inserts the row for a request that is about to be sent (or refused by the cloud — the row is
     * written either way, then completed with the refusal code).
     *
     * @return the persisted row id holder, used by {@link #complete}
     */
    public UUID begin(UUID auditId, TunnelActor actor, TunnelOp op, JsonNode params) {
        EdgeDataAccessLog row = new EdgeDataAccessLog();
        row.setAuditId(auditId);
        row.setPspId(actor.pspId());
        row.setActorKind(actor.kind().name());
        row.setActorUserId(actor.userId());
        row.setActorRole(actor.role());
        row.setPurpose(actor.purpose().name());
        row.setCaseId(actor.caseId());
        row.setAlertId(actor.alertId());
        row.setFilingRef(actor.filingRef());
        boolean support = actor.kind() == TunnelActor.Kind.PLATFORM_USER;
        row.setSupportAccess(support);
        row.setSupportReason(support ? actor.supportReason() : null);
        row.setOp(op.wire());
        row.setParamsSummary(summarize(op, params).toString());
        row.setParamsDigest(Jcs.sha256Hex(params));
        row.setRequestedAt(clock.instant());
        repository.save(row);
        return auditId;
    }

    /** Completes the row on the terminal frame (or on the cloud-side refusal/failure). */
    public void complete(UUID auditId, Collection<String> sitesContacted, Collection<String> sitesAnswered,
                         int itemCount, long bytes, String outcome) {
        EdgeDataAccessLog row = repository.findByAuditId(auditId)
                .orElseThrow(() -> new IllegalStateException("audit row missing for a tunnel request"));
        row.setSitesContacted(join(sitesContacted));
        row.setSitesAnswered(join(sitesAnswered));
        row.setItemCount(itemCount);
        row.setBytes(bytes);
        row.setOutcome(outcome);
        row.setCompletedAt(clock.instant());
        repository.save(row);
    }

    /** Comma-joined site ids (site ids are Control Plane master data, never transaction data). */
    private static String join(Collection<String> sites) {
        return sites == null || sites.isEmpty() ? null : String.join(",", sites.stream().sorted().toList());
    }

    /**
     * The §7.15 {@code params_summary}: only the allow-listed members, ids reduced to counts and
     * {@code txnRef} reduced to a presence flag.
     */
    public static ObjectNode summarize(TunnelOp op, JsonNode params) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        if (params == null || !params.isObject()) {
            return out;
        }
        JsonNode filter = op == TunnelOp.EXPORT_STREAM ? params.path("filter") : params;
        copyText(filter, out, "from");
        copyText(filter, out, "to");
        copyText(filter, out, "merchantId");
        if (filter.path("decisions").isArray()) {
            out.set("decisions", filter.get("decisions").deepCopy());
        }
        copyNumber(filter, out, "ruleId");
        copyNumber(filter, out, "minScore");
        copyBoolean(filter, out, "ctrRequired");
        copyBoolean(filter, out, "sanctionsHit");
        if (filter.hasNonNull("txnRef") || params.hasNonNull("txnRef")) {
            out.put("byTxnRef", true);
        }
        if (params.path("edgeAssessmentIds").isArray()) {
            out.put("idCount", params.get("edgeAssessmentIds").size());
        } else if (params.hasNonNull("edgeAssessmentId")) {
            out.put("idCount", 1);
        }
        if (params.path("linkBy").isArray()) {
            out.set("linkBy", params.get("linkBy").deepCopy());
        }
        copyNumber(params, out, "depth");
        copyNumber(params, out, "windowDays");
        copyText(params, out, "kind");
        copyNumber(params, out, "maxRows");
        if (params.path("columns").isArray()) {
            out.put("columnCount", params.get("columns").size());
        }
        if (op == TunnelOp.LEDGER_BACKFILL) {
            copyText(params, out, "phase");
            copyNumber(params, out, "batchSeq");
            copyNumber(params, out, "expectedRows");
            if (params.path("rows").isArray()) {
                out.put("rowCount", params.get("rows").size());
            }
        }
        return out;
    }

    /** Copies a textual member when present. */
    private static void copyText(JsonNode from, ObjectNode to, String name) {
        if (from.path(name).isTextual()) {
            to.put(name, from.get(name).asText());
        }
    }

    /** Copies an integral member when present. */
    private static void copyNumber(JsonNode from, ObjectNode to, String name) {
        if (from.path(name).isIntegralNumber()) {
            to.put(name, from.get(name).asLong());
        }
    }

    /** Copies a boolean member when present. */
    private static void copyBoolean(JsonNode from, ObjectNode to, String name) {
        if (from.path(name).isBoolean()) {
            to.put(name, from.get(name).asBoolean());
        }
    }
}
