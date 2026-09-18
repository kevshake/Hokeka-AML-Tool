package com.posgateway.aml.exception;

/**
 * Thrown when a notification (email, Slack, webhook, etc.) cannot be delivered.
 *
 * <p>Callers should catch this when they need to record/audit delivery failures
 * (e.g. password reset emails, compliance callbacks). It is a {@link RuntimeException}
 * so it does not pollute service signatures, but is mapped to a 5xx by the
 * {@code GlobalExceptionHandler} (RuntimeException catch-all).
 */
public class NotificationException extends RuntimeException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
