package com.posgateway.aml.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The request is valid but conflicts with the resource's current state (for example approving a rule
 * version that is no longer pending). Rendered as HTTP 409 by
 * {@link GlobalExceptionHandler#handleResponseStatus}; a plain IllegalStateException fell through to
 * the catch-all handler and surfaced as a 500.
 */
public class ConflictException extends ResponseStatusException {

    /** @param message reason shown to the caller */
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
