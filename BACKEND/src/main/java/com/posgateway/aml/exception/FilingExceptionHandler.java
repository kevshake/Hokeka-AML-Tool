package com.posgateway.aml.exception;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.service.filing.FiledDocumentStore;
import com.posgateway.aml.service.filing.FilingException;
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
 * Answers a refused filing (R9) with its JSON body and status — 503 for an offline or partial Edge
 * (retryable: nothing was filed), 409 for state conflicts, 403/404 for access — and a failed
 * filed-document integrity check with 500. Ordered first so these win over the generic handler.
 * Logs codes only: the bodies never carry a transaction value.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FilingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FilingExceptionHandler.class);

    /** The refusal body, never cached. */
    @ExceptionHandler(FilingException.class)
    public ResponseEntity<ObjectNode> handle(FilingException e) {
        log.info("Filing refused: {}", e.code());
        return ResponseEntity.status(e.httpStatus()).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON).body(e.toJson());
    }

    /** A stored document that fails its seal is never served. */
    @ExceptionHandler(FiledDocumentStore.IntegrityException.class)
    public ResponseEntity<ObjectNode> handleIntegrity(FiledDocumentStore.IntegrityException e) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("code", "FILED_DOCUMENT_INTEGRITY");
        body.put("httpStatus", 500);
        body.put("message", e.getMessage());
        body.put("retryable", false);
        return ResponseEntity.status(500).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
