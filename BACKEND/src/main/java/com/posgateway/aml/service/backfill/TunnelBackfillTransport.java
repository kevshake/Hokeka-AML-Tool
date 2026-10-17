package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataRequest;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.edge.tunnel.TunnelOp;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@link BackfillTransport} over the R4 {@code EdgeDataGateway}: schema validation, sealing, routing
 * to a live session of the named site, the §7.15 audit row and the §7.11 errors all come from the
 * gateway. Without the tunnel on this Control Plane every call is {@code 503 EDGE_OFFLINE}.
 */
@Component
public class TunnelBackfillTransport implements BackfillTransport {

    /** The SYSTEM job name of every back-fill request ({@code userId = system:backfill}). */
    public static final String JOB = "backfill";

    private final EdgeTunnelAccess tunnel;

    /** Uses the tunnel beans when this Control Plane runs the tunnel server. */
    public TunnelBackfillTransport(EdgeTunnelAccess tunnel) {
        this.tunnel = tunnel;
    }

    /** One ledger.backfill phase on the site (§7.6: 60 s per phase, exactly one site). */
    @Override
    public ObjectNode backfill(long pspId, String siteId, ObjectNode params) {
        EdgeDataResult r = call(pspId, siteId, TunnelOp.LEDGER_BACKFILL, params, TunnelActor.Purpose.LEDGER_BACKFILL);
        return single(r);
    }

    /** ledger.health of the site, as a ledger operation. */
    @Override
    public ObjectNode health(long pspId, String siteId) {
        EdgeDataResult r = call(pspId, siteId, TunnelOp.LEDGER_HEALTH, JsonNodeFactory.instance.objectNode(),
                TunnelActor.Purpose.LEDGER_OPERATIONS);
        return single(r);
    }

    /** txn.batchGet on the site, for the verification sample. */
    @Override
    public BatchGet batchGet(long pspId, String siteId, List<UUID> edgeAssessmentIds) {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        ArrayNode ids = params.putArray("edgeAssessmentIds");
        edgeAssessmentIds.forEach(id -> ids.add(id.toString()));
        EdgeDataResult r = call(pspId, siteId, TunnelOp.TXN_BATCH_GET, params, TunnelActor.Purpose.LEDGER_BACKFILL);
        if (!r.unavailableSites().isEmpty()) {
            throw new EdgeDataException(TunnelError.offline(siteId, r.unavailableSites().get(0).lastSeenAt()));
        }
        return new BatchGet(r.items(), r.missing());
    }

    /** txn.get by txnRef on the site; NOT_FOUND is an empty answer, not a failure. */
    @Override
    public Optional<ObjectNode> getByTxnRef(long pspId, String siteId, String txnRef) {
        ObjectNode params = JsonNodeFactory.instance.objectNode().put("txnRef", txnRef);
        try {
            EdgeDataResult r = call(pspId, siteId, TunnelOp.TXN_GET, params, TunnelActor.Purpose.LEDGER_BACKFILL);
            return r.items().isEmpty() ? Optional.empty() : Optional.of(r.items().get(0));
        } catch (EdgeDataException e) {
            if (e.error().code() == TunnelErrorCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** One gateway request restricted to {@code siteId}, as {@code system:backfill}. */
    private EdgeDataResult call(long pspId, String siteId, TunnelOp op, ObjectNode params, TunnelActor.Purpose purpose) {
        TunnelActor actor = TunnelActor.system(JOB, pspId, purpose);
        return tunnel.gateway().request(EdgeDataRequest.of(pspId, op, params, actor).onSites(Set.of(siteId)));
    }

    /** The one item of a single-item answer; a missing item is an Edge defect (INTERNAL). */
    private static ObjectNode single(EdgeDataResult r) {
        if (r.items().isEmpty()) {
            if (!r.unavailableSites().isEmpty()) {
                EdgeDataResult.UnavailableSite u = r.unavailableSites().get(0);
                throw new EdgeDataException(u.code() == TunnelErrorCode.EDGE_OFFLINE
                        ? TunnelError.offline(u.siteId(), u.lastSeenAt())
                        : TunnelError.of(u.code(), "site did not answer").withSite(u.siteId()));
            }
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.INTERNAL, "the Edge sent no acknowledgement"));
        }
        return r.items().get(0);
    }
}
