package com.posgateway.aml.service.filing;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.TunnelError;

import java.util.List;

/**
 * A filing that was refused. Nothing is filed and nothing changes when this is thrown, so a
 * retryable refusal (Edge offline, partial answer, busy, timeout) can simply be retried. The body
 * carries codes, counts and site ids only — never a transaction value.
 */
public class FilingException extends RuntimeException {

    /** Refusal codes. */
    public enum Code {
        /** The caller may not file this record (role, PSP, permission). */
        FILING_NOT_ALLOWED(403, false),
        /** No such record for the caller. */
        FILING_NOT_FOUND(404, false),
        /** The record is not in a fileable state (e.g. SAR not APPROVED, deadline not CTR). */
        FILING_STATE_CONFLICT(409, false),
        /** The record has already been filed. */
        ALREADY_FILED(409, false),
        /** The record changed while its data was being pulled. */
        FILING_CONCURRENT_CHANGE(409, true),
        /** The Edge answered only partly or without some transactions: nothing was filed. */
        FILING_INCOMPLETE(503, true),
        /** The Edge pull failed as a whole (offline, timeout, busy, refused): nothing was filed. */
        EDGE_PULL_FAILED(503, true),
        /** The CTR aggregate does not reach the reporting threshold. */
        CTR_BELOW_THRESHOLD(409, false),
        /** The document could not be stored (encryption key, database). */
        FILING_STORE_FAILED(500, true);

        private final int httpStatus;
        private final boolean retryable;

        /** Binds status and retry flag. */
        Code(int httpStatus, boolean retryable) {
            this.httpStatus = httpStatus;
            this.retryable = retryable;
        }

        /** HTTP status of the refusal. */
        public int httpStatus() {
            return httpStatus;
        }

        /** Whether retrying can succeed. */
        public boolean retryable() {
            return retryable;
        }
    }

    private final Code code;
    private final int httpStatus;
    private final boolean retryable;
    private final String filingRef;
    private final TunnelError edgeError;
    private final List<EdgeDataResult.UnavailableSite> unavailableSites;
    private final int missingCount;

    /** A refusal with the code's default status. */
    public FilingException(Code code, String message, String filingRef) {
        this(code, code.httpStatus(), code.retryable(), message, filingRef, null, List.of(), 0);
    }

    /** A refusal with every detail. */
    public FilingException(Code code, int httpStatus, boolean retryable, String message, String filingRef,
                           TunnelError edgeError, List<EdgeDataResult.UnavailableSite> unavailableSites,
                           int missingCount) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.filingRef = filingRef;
        this.edgeError = edgeError;
        this.unavailableSites = unavailableSites == null ? List.of() : List.copyOf(unavailableSites);
        this.missingCount = missingCount;
    }

    /** The Edge pull failed as a whole: status and retry flag follow the §7.11 error. */
    public static FilingException edgeFailure(TunnelError err, String filingRef) {
        return new FilingException(Code.EDGE_PULL_FAILED, err.httpStatus(), err.retryable(),
                "the transaction data could not be pulled from the Edge; nothing was filed", filingRef, err,
                List.of(), 0);
    }

    /** The refusal code. */
    public Code code() {
        return code;
    }

    /** HTTP status. */
    public int httpStatus() {
        return httpStatus;
    }

    /** JSON body: {@code {code, httpStatus, message, retryable, filingRef?, edgeError?, unavailableSites?, missingCount?}}. */
    public ObjectNode toJson() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("code", code.name());
        n.put("httpStatus", httpStatus);
        n.put("message", getMessage());
        n.put("retryable", retryable);
        if (filingRef != null) {
            n.put("filingRef", filingRef);
        }
        if (edgeError != null) {
            n.set("edgeError", edgeError.toJson());
        }
        if (!unavailableSites.isEmpty()) {
            ArrayNode a = n.putArray("unavailableSites");
            for (EdgeDataResult.UnavailableSite u : unavailableSites) {
                ObjectNode s = a.addObject();
                if (u.siteId() != null) {
                    s.put("siteId", u.siteId());
                }
                s.put("code", u.code().name());
            }
        }
        if (missingCount > 0) {
            n.put("missingCount", missingCount);
        }
        return n;
    }
}
