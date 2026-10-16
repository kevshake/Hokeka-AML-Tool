package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.LuhnGuard;

import java.util.regex.Pattern;

/**
 * A request or frame refused with a §7.11 error. The message is operator text and never carries a
 * transaction value; {@code param} is a JSON pointer, never the value at it.
 */
public class TunnelException extends Exception {

    private static final Pattern POINTER = Pattern.compile("^(/[A-Za-z0-9_.~-]*)*$");

    private final TunnelErrorCode code;
    private final String param;
    private final Long retryAfterMs;

    /** An error without a param pointer. */
    public TunnelException(TunnelErrorCode code, String message) {
        this(code, message, null, null);
    }

    /**
     * @param code         the §7.11 code
     * @param message      operator text (no transaction values)
     * @param param        JSON pointer of the offending member, or null
     * @param retryAfterMs retry hint (EDGE_BUSY), or null
     */
    public TunnelException(TunnelErrorCode code, String message, String param, Long retryAfterMs) {
        super(message);
        this.code = code;
        this.param = param;
        this.retryAfterMs = retryAfterMs;
    }

    /** The error code. */
    public TunnelErrorCode code() {
        return code;
    }

    /** The JSON pointer, or null. */
    public String param() {
        return param;
    }

    /**
     * The {@code tunnel/error.schema.json} body. A pointer that does not fit the schema's pointer
     * syntax is shortened to its longest valid prefix; a message is cut to 512 characters and, as
     * a last line of defence, replaced when the Luhn guard would flag it.
     */
    public ObjectNode toBody(ObjectMapper mapper) {
        ObjectNode body = mapper.createObjectNode();
        body.put("code", code.name());
        body.put("httpStatus", code.httpStatus());
        String message = getMessage() == null ? code.name() : getMessage();
        if (message.length() > 512) {
            message = message.substring(0, 512);
        }
        if (LuhnGuard.panLike(message)) {
            message = code.name();
        }
        body.put("message", message);
        body.put("retryable", code.retryable());
        if (code == TunnelErrorCode.EDGE_BUSY) {
            body.put("retryAfterMs", retryAfterMs == null ? 500L : Math.max(0L, Math.min(600_000L, retryAfterMs)));
        } else if (retryAfterMs != null) {
            body.put("retryAfterMs", Math.max(0L, Math.min(600_000L, retryAfterMs)));
        }
        String p = safePointer(param);
        if (p != null) {
            body.put("param", p);
        }
        return body;
    }

    /** The longest prefix of {@code pointer} that matches the schema's pointer syntax, or null. */
    static String safePointer(String pointer) {
        if (pointer == null) {
            return null;
        }
        String p = pointer.length() > 256 ? pointer.substring(0, 256) : pointer;
        while (!p.isEmpty() && !POINTER.matcher(p).matches()) {
            int cut = p.lastIndexOf('/');
            p = cut <= 0 ? "" : p.substring(0, cut);
        }
        return p.isEmpty() ? null : p;
    }
}
