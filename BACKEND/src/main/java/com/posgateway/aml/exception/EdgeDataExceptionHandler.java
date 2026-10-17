package com.posgateway.aml.exception;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a failed tunnel-backed call with the contract §7.11 body and its HTTP status (offline 503
 * with {@code lastSeenAt}, timeout 504, busy 429 with {@code Retry-After}, support access 403, …)
 * instead of the generic 500 of {@link GlobalExceptionHandler}. Ordered first so it wins for
 * {@link EdgeDataException}. Logs the code only: the body never carries a transaction value.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EdgeDataExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EdgeDataExceptionHandler.class);

    /** The §7.11 error response, never cached. */
    @ExceptionHandler(EdgeDataException.class)
    public ResponseEntity<ObjectNode> handle(EdgeDataException e) {
        TunnelError err = e.error();
        log.debug("Edge data call answered {}", err.code());
        ResponseEntity.BodyBuilder b = ResponseEntity.status(err.httpStatus())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON);
        if (err.retryAfterMs() != null) {
            b.header("Retry-After", String.valueOf(Math.max(1, (err.retryAfterMs() + 999) / 1000)));
        }
        return b.body(err.toJson());
    }
}
