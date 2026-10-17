package com.posgateway.aml.dto.edgedata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelTime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the live transaction pull behind a workflow view went (alert detail, SAR detail, case
 * timeline, case network): the Console's loading / offline / partial / timeout / busy states
 * without failing the view itself, whose cloud-held fields are always returned.
 *
 * @param source           {@code EDGE} or {@code CLOUD_LEGACY}; {@code null} when nothing was pulled
 * @param state            {@code OK}, {@code PARTIAL}, {@code NOT_REQUESTED}, {@code NONE} (no
 *                         referenced transaction) or the §7.11 error code of a failed pull
 * @param incomplete       a site did not answer
 * @param unavailableSites sites that did not answer ({@code {siteId, code, lastSeenAt?}})
 * @param missing          referenced ids no answering site holds
 * @param error            the §7.11 body of a failed pull
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EdgePullStatus(String source, String state, boolean incomplete,
                             List<Map<String, Object>> unavailableSites, List<String> missing,
                             Map<String, Object> error) {

    /** The caller asked not to pull. */
    public static EdgePullStatus notRequested() {
        return new EdgePullStatus(null, "NOT_REQUESTED", false, List.of(), List.of(), null);
    }

    /** The record references no transaction: nothing to pull. */
    public static EdgePullStatus none() {
        return new EdgePullStatus(null, "NONE", false, List.of(), List.of(), null);
    }

    /** A pull that answered (fully or partly). */
    public static EdgePullStatus answered(String source, boolean incomplete,
                                          List<EdgeDataResult.UnavailableSite> unavailable, List<String> missing) {
        List<Map<String, Object>> sites = new ArrayList<>();
        for (EdgeDataResult.UnavailableSite u : unavailable) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("siteId", u.siteId());
            m.put("code", u.code().name());
            if (u.lastSeenAt() != null) {
                m.put("lastSeenAt", TunnelTime.formatSeconds(u.lastSeenAt()));
            }
            sites.add(m);
        }
        return new EdgePullStatus(source, incomplete ? "PARTIAL" : "OK", incomplete, sites, List.copyOf(missing), null);
    }

    /** A pull that failed as a whole: the view still renders its cloud-held fields. */
    public static EdgePullStatus failed(String source, TunnelError error) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", error.code().name());
        e.put("httpStatus", error.httpStatus());
        e.put("message", error.message());
        e.put("retryable", error.retryable());
        if (error.retryAfterMs() != null) {
            e.put("retryAfterMs", error.retryAfterMs());
        }
        if (error.lastSeenAt() != null) {
            e.put("lastSeenAt", TunnelTime.formatSeconds(error.lastSeenAt()));
        }
        if (error.siteId() != null) {
            e.put("siteId", error.siteId());
        }
        return new EdgePullStatus(source, error.code().name(), true, List.of(), List.of(), e);
    }
}
