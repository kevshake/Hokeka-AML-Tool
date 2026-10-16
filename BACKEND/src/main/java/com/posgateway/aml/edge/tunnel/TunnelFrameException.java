package com.posgateway.aml.edge.tunnel;

/**
 * A frame failed one of the §7.4 receive checks (schema, seal, replay, binding, body). Carries the
 * §7.11 code to answer with; the message is a fixed phrase that never contains frame content.
 */
public class TunnelFrameException extends Exception {

    private final TunnelErrorCode code;

    /** Creates the exception; {@code reason} must be a constant phrase (no frame data). */
    public TunnelFrameException(TunnelErrorCode code, String reason) {
        super(reason, null, false, false);
        this.code = code;
    }

    /** The §7.11 code for this failure. */
    public TunnelErrorCode code() {
        return code;
    }
}
