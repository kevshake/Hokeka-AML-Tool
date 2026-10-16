package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The merged answer of a tunnel pull (§7.13, §7.14). Lives only for the request that pulled it
 * (§7.16): it is never cached, logged or persisted by the tunnel.
 *
 * @param auditId          id shared by the cloud and Edge audit rows
 * @param op               the op
 * @param items            schema-validated items, merged across sites
 * @param nextCursor       list ops: composite cursor for the next page, {@code null} when done
 * @param incomplete       some sites did not answer, or a site reported an incomplete page
 * @param incompleteReason the first site-reported reason ({@code SCAN_LIMIT}, {@code DEPTH_LIMIT},
 *                         {@code DEADLINE}), or {@code null}
 * @param unavailableSites sites that did not answer and why
 * @param missing          batchGet / filing.extract ids absent on every site that answered
 * @param totalRows        export.stream only
 * @param itemsBySite      per-site items (ledger.health and diagnostics)
 * @param sitesAnswered    sites that answered
 */
public record EdgeDataResult(UUID auditId, TunnelOp op, List<ObjectNode> items, String nextCursor, boolean incomplete,
                             String incompleteReason, List<UnavailableSite> unavailableSites, List<String> missing,
                             Long totalRows, Map<String, List<ObjectNode>> itemsBySite, List<String> sitesAnswered) {

    /** A site that did not answer (§7.13 {@code unavailableSites[]}). */
    public record UnavailableSite(String siteId, TunnelErrorCode code, Instant lastSeenAt) {

        /** {@code {siteId, code, lastSeenAt?}} as the Console receives it. */
        public ObjectNode toJson() {
            ObjectNode n = JsonNodeFactory.instance.objectNode();
            n.put("siteId", siteId);
            n.put("code", code.name());
            if (lastSeenAt != null) {
                n.put("lastSeenAt", TunnelTime.formatSeconds(lastSeenAt));
            }
            return n;
        }
    }

    /**
     * The §7.14 list envelope {@code {items, nextCursor, incomplete, unavailableSites}} with every
     * item tagged {@code source: "EDGE"}. Built per response, never stored.
     */
    public ObjectNode toConsoleEnvelope() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        ArrayNode arr = n.putArray("items");
        for (ObjectNode item : items) {
            ObjectNode copy = item.deepCopy();
            copy.put("source", "EDGE");
            arr.add(copy);
        }
        if (nextCursor != null) {
            n.put("nextCursor", nextCursor);
        } else {
            n.putNull("nextCursor");
        }
        n.put("incomplete", incomplete);
        ArrayNode u = n.putArray("unavailableSites");
        unavailableSites.forEach(s -> u.add(s.toJson()));
        if (!missing.isEmpty()) {
            ArrayNode m = n.putArray("missing");
            missing.forEach(m::add);
        }
        return n;
    }
}
