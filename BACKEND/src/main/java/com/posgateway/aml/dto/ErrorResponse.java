package com.posgateway.aml.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized Error Response DTO
 * Provides consistent error response structure across all endpoints
 */
public class ErrorResponse {
    private String status;
    private String message;
    private String errorCode;
    private int httpStatus;
    private LocalDateTime timestamp;
    private String path;
    private String method;
    private Map<String, Object> details;
    private String traceId; // For request tracing

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String status, String message, int httpStatus) {
        this();
        this.status = status;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public ErrorResponse(String status, String message, String errorCode, int httpStatus) {
        this(status, message, httpStatus);
        this.errorCode = errorCode;
    }

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    public static class ErrorResponseBuilder {
        private String status;
        private String message;
        private String errorCode;
        private int httpStatus;
        private LocalDateTime timestamp = LocalDateTime.now();
        private String path;
        private String method;
        private Map<String, Object> details;
        private String traceId;

        public ErrorResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ErrorResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ErrorResponseBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public ErrorResponseBuilder httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public ErrorResponseBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ErrorResponseBuilder path(String path) {
            this.path = path;
            return this;
        }

        public ErrorResponseBuilder method(String method) {
            this.method = method;
            return this;
        }

        public ErrorResponseBuilder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public ErrorResponseBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public ErrorResponse build() {
            ErrorResponse response = new ErrorResponse();
            response.setStatus(status);
            response.setMessage(message);
            response.setErrorCode(errorCode);
            response.setHttpStatus(httpStatus);
            response.setTimestamp(timestamp);
            response.setPath(path);
            response.setMethod(method);
            response.setDetails(details);
            response.setTraceId(traceId);
            return response;
        }
    }
}

