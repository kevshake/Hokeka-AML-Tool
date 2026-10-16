package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * One pull of PSP transaction data through the Edge Tunnel, as cloud code (R6) asks for it.
 *
 * @param pspId      PSP whose Edge Sites are addressed (must equal {@code actor.pspId})
 * @param op         the op
 * @param params     op params ({@code tunnel/ops/<op>.params.schema.json}); validated before sending
 * @param actor      who the cloud acts for, incl. purpose (§7.5)
 * @param pageLimit  list ops: page size 1..200 (default 50); ignored otherwise
 * @param cursor     list ops: the composite cursor returned by the previous page, or {@code null}
 * @param deadlineMs budget; {@code null} = the op's §7.6 default
 * @param sites      restrict to these sites (e.g. the site of a decision record for a get by
 *                   {@code edgeAssessmentId}, or the PSP-chosen backfill site); {@code null} = all
 */
public record EdgeDataRequest(long pspId, TunnelOp op, ObjectNode params, TunnelActor actor, Integer pageLimit,
                              String cursor, Long deadlineMs, Set<String> sites) {

    /** Default page size for list ops when the caller gives none. */
    public static final int DEFAULT_PAGE_LIMIT = 50;

    /** A request with all optional members defaulted. */
    public static EdgeDataRequest of(long pspId, TunnelOp op, ObjectNode params, TunnelActor actor) {
        return new EdgeDataRequest(pspId, op, params, actor, null, null, null, null);
    }

    /** Copy with a page size and cursor. */
    public EdgeDataRequest page(int limit, String nextCursor) {
        return new EdgeDataRequest(pspId, op, params, actor, limit, nextCursor, deadlineMs, sites);
    }

    /** Copy restricted to the given sites. */
    public EdgeDataRequest onSites(Set<String> siteIds) {
        return new EdgeDataRequest(pspId, op, params, actor, pageLimit, cursor, deadlineMs, Set.copyOf(siteIds));
    }

    /** Copy with an explicit deadline. */
    public EdgeDataRequest withDeadline(long ms) {
        return new EdgeDataRequest(pspId, op, params, actor, pageLimit, cursor, ms, sites);
    }

    /** Effective deadline: explicit, else the op default. */
    long effectiveDeadlineMs() {
        return deadlineMs != null ? deadlineMs : op.defaultDeadlineMs();
    }

    /** Effective page size for list ops. */
    int effectivePageLimit() {
        return pageLimit != null ? pageLimit : DEFAULT_PAGE_LIMIT;
    }
}
