package com.posgateway.aml.edge.tunnel;

/**
 * Thrown by {@link EdgeDataGateway} when a tunnel pull fails as a whole (§7.11, §7.13: when no
 * site answers, the "best" failure). The carried {@link TunnelError} is the Console JSON body and
 * its {@code httpStatus} the HTTP status to answer with.
 *
 * <p>The exception message is the error code only, so a stack trace in a log can never carry
 * transaction data (§7.16: no payloads in exception messages).
 */
public class EdgeDataException extends RuntimeException {

    private final transient TunnelError error;

    /** Wraps a contract error; the message is deliberately just the code. */
    public EdgeDataException(TunnelError error) {
        super(error.code().name(), null, false, false);
        this.error = error;
    }

    /** The §7.11 error body. */
    public TunnelError error() {
        return error;
    }
}
