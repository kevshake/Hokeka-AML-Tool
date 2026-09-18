package com.posgateway.aml.service.edge;

/**
 * Thrown when an edge enrollment / lifecycle operation is refused.
 *
 * <p>Carries the HTTP status the control plane should answer with, so the fail-closed behaviour of
 * {@code docs/architecture/edge-channel-contract.md} §2 is decided in the service (the security
 * decision point) rather than re-derived in each controller.
 */
public class EdgeEnrollmentException extends RuntimeException {

    private final int status;

    public EdgeEnrollmentException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    /** 403 — the caller is not authorised (unknown, unapproved, suspended or revoked edge). */
    public static EdgeEnrollmentException forbidden(String message) {
        return new EdgeEnrollmentException(403, message);
    }

    /** 404 — no such node for this tenant. */
    public static EdgeEnrollmentException notFound(String message) {
        return new EdgeEnrollmentException(404, message);
    }

    /** 409 — the requested transition is illegal for the node's current state. */
    public static EdgeEnrollmentException conflict(String message) {
        return new EdgeEnrollmentException(409, message);
    }

    /** 400 — malformed input (bad key encoding, missing edge id, ...). */
    public static EdgeEnrollmentException badRequest(String message) {
        return new EdgeEnrollmentException(400, message);
    }
}
