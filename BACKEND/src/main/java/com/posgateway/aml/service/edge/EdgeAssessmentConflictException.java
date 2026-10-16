package com.posgateway.aml.service.edge;

/** Raised when an edge assessment id is reused across PSPs or transactions. */
public class EdgeAssessmentConflictException extends RuntimeException {

    /** Builds a conflict exception with an operator-safe message (no secrets). */
    public EdgeAssessmentConflictException(String message) {
        super(message);
    }
}
