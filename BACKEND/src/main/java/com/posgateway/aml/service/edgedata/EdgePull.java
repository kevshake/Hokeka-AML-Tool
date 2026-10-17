package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The answer to one Console call, made of one or more tunnel pulls (a batchGet across sites is
 * one pull per site group). Lives only for the request (contract §7.16): never cached, logged or
 * persisted.
 *
 * @param items            validated items in response order
 * @param nextCursor       list calls: composite cursor of the next page, else {@code null}
 * @param incomplete       a site did not answer or reported an incomplete page
 * @param incompleteReason first site-reported reason, if any
 * @param unavailableSites sites that did not answer
 * @param missing          batchGet ids absent on every site that answered
 * @param auditIds         the access-log ids of the pulls behind this answer
 */
public record EdgePull(List<ObjectNode> items, String nextCursor, boolean incomplete, String incompleteReason,
                       List<EdgeDataResult.UnavailableSite> unavailableSites, List<String> missing,
                       List<UUID> auditIds) {

    /** Wraps one gateway result. */
    public static EdgePull of(EdgeDataResult r) {
        return new EdgePull(r.items(), r.nextCursor(), r.incomplete(), r.incompleteReason(), r.unavailableSites(),
                r.missing(), List.of(r.auditId()));
    }

    /** An answer that needed no pull (nothing was asked for). */
    public static EdgePull empty() {
        return new EdgePull(List.of(), null, false, null, List.of(), List.of(), List.of());
    }

    /**
     * Combines per-site-group batchGet answers: items follow {@code requestedOrder}; an id is missing
     * only if a group that answered reported it missing; unavailable sites are deduplicated by site.
     */
    public static EdgePull mergeGets(List<String> requestedOrder, List<EdgePull> parts,
                                     List<EdgeDataResult.UnavailableSite> failedGroups) {
        Map<String, ObjectNode> byId = new LinkedHashMap<>();
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        Map<String, EdgeDataResult.UnavailableSite> unavailable = new LinkedHashMap<>();
        List<UUID> audits = new ArrayList<>();
        boolean incomplete = !failedGroups.isEmpty();
        String reason = null;
        for (EdgePull p : parts) {
            p.items().forEach(i -> byId.putIfAbsent(i.path("edgeAssessmentId").asText(), i));
            missing.addAll(p.missing());
            p.unavailableSites().forEach(u -> unavailable.putIfAbsent(u.siteId(), u));
            audits.addAll(p.auditIds());
            incomplete |= p.incomplete();
            if (reason == null) {
                reason = p.incompleteReason();
            }
        }
        failedGroups.forEach(u -> unavailable.putIfAbsent(u.siteId(), u));
        List<ObjectNode> ordered = new ArrayList<>();
        for (String id : requestedOrder) {
            ObjectNode item = byId.get(id);
            if (item != null) {
                ordered.add(item);
                missing.remove(id);
            }
        }
        return new EdgePull(List.copyOf(ordered), null, incomplete, reason, List.copyOf(unavailable.values()),
                List.copyOf(missing), List.copyOf(audits));
    }
}
