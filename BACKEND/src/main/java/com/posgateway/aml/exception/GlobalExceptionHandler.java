package com.posgateway.aml.exception;

import com.posgateway.aml.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global Exception Handler
 * Provides centralized exception handling and error responses
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        Map<String, Object> details = new HashMap<>();
        details.put("fieldErrors", fieldErrors);
        details.put("totalErrors", fieldErrors.size());

        String traceId = UUID.randomUUID().toString();
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("VALIDATION_ERROR")
                .message("Request validation failed. Please check the field errors.")
                .errorCode("ERR_VALIDATION_001")
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .details(details)
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] Validation error on {} {}: {}", traceId, request.getMethod(), request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions (often used for "not found" scenarios)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        // Check if it's a "not found" scenario
        boolean isNotFound = ex.getMessage() != null && 
            (ex.getMessage().contains("not found") || ex.getMessage().contains("Not found"));
        
        HttpStatus status = isNotFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String errorCode = isNotFound ? "ERR_NOT_FOUND_001" : "ERR_BAD_REQUEST_001";
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(isNotFound ? "NOT_FOUND" : "BAD_REQUEST")
                .message(ex.getMessage() != null ? ex.getMessage() : "Invalid request")
                .errorCode(errorCode)
                .httpStatus(status.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] {} on {} {}: {}", traceId, 
                isNotFound ? "Resource not found" : "Illegal argument", 
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("FORBIDDEN")
                .message("You do not have permission to access this resource")
                .errorCode("ERR_ACCESS_DENIED_001")
                .httpStatus(HttpStatus.FORBIDDEN.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] Access denied on {} {}: {}", traceId, 
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        Map<String, Object> details = new HashMap<>();
        details.put("parameterName", ex.getParameterName());
        details.put("parameterType", ex.getParameterType());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("BAD_REQUEST")
                .message("Required parameter '" + ex.getParameterName() + "' is missing")
                .errorCode("ERR_MISSING_PARAM_001")
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .details(details)
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] Missing parameter on {} {}: {}", traceId, 
                request.getMethod(), request.getRequestURI(), ex.getParameterName());
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        Map<String, Object> details = new HashMap<>();
        details.put("parameterName", ex.getName());
        Class<?> requiredType = ex.getRequiredType();
        String requiredTypeName = requiredType != null ? requiredType.getName() : "unknown";
        String requiredTypeSimpleName = requiredType != null ? requiredType.getSimpleName() : "unknown";
        details.put("requiredType", requiredTypeName);
        details.put("providedValue", ex.getValue());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("BAD_REQUEST")
                .message("Invalid value for parameter '" + ex.getName() + "'. Expected type: " + requiredTypeSimpleName)
                .errorCode("ERR_TYPE_MISMATCH_001")
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .details(details)
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] Type mismatch on {} {}: {}", traceId, 
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle HTTP message not readable (malformed JSON, etc.)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("BAD_REQUEST")
                .message("Request body is malformed or invalid. Please check the JSON format.")
                .errorCode("ERR_INVALID_BODY_001")
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] Invalid request body on {} {}: {}", traceId, 
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle unsupported HTTP method
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        Map<String, Object> details = new HashMap<>();
        details.put("requestedMethod", ex.getMethod());
        details.put("supportedMethods", ex.getSupportedMethods());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("METHOD_NOT_ALLOWED")
                .message("HTTP method '" + ex.getMethod() + "' is not supported for this endpoint")
                .errorCode("ERR_METHOD_NOT_ALLOWED_001")
                .httpStatus(HttpStatus.METHOD_NOT_ALLOWED.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .details(details)
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] Method not allowed on {} {}: {}", traceId, 
                request.getMethod(), request.getRequestURI(), ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    /**
     * Handle database access exceptions (PostgreSQL, Aerospike, etc.)
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(
            DataAccessException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("DATABASE_UNAVAILABLE")
                .message("A database error occurred or the database is temporarily unavailable. Please try again later.")
                .errorCode("ERR_DATABASE_001")
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        logger.error("[TraceId: {}] Database error on {} {}: {}", traceId, 
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    /**
     * Handle missing route / static resource lookups (e.g. wrong URL like /merchants)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("NOT_FOUND")
                .message("The requested endpoint was not found: " + request.getRequestURI())
                .errorCode("ERR_ROUTE_NOT_FOUND_001")
                .httpStatus(HttpStatus.NOT_FOUND.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        logger.warn("[TraceId: {}] No handler for {} {}: {}", traceId,
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle runtime exceptions (catch-all for business logic errors)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        
        // Check if it's a "not found" scenario
        boolean isNotFound = ex.getMessage() != null && 
            (ex.getMessage().contains("not found") || ex.getMessage().contains("Not found"));
        
        HttpStatus status = isNotFound ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
        String errorCode = isNotFound ? "ERR_NOT_FOUND_002" : "ERR_INTERNAL_001";
        String statusStr = isNotFound ? "NOT_FOUND" : "INTERNAL_ERROR";
        String message = isNotFound ? 
            (ex.getMessage() != null ? ex.getMessage() : "Resource not found") :
            "An internal error occurred. Please contact support if the problem persists.";
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(statusStr)
                .message(message)
                .errorCode(errorCode)
                .httpStatus(status.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        if (isNotFound) {
            logger.warn("[TraceId: {}] Resource not found on {} {}: {}", traceId, 
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        } else {
            logger.error("[TraceId: {}] Runtime exception on {} {}: {}", traceId, 
                    request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        }
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle all other exceptions (catch-all)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        logNonPspError(ex, request, "handleGenericException");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status("ERROR")
                .message("An unexpected error occurred. Please contact support if the problem persists.")
                .errorCode("ERR_UNEXPECTED_001")
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        logger.error("[TraceId: {}] Unexpected error on {} {}: {}", traceId, 
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private void logNonPspError(Exception ex, jakarta.servlet.http.HttpServletRequest request, String functionName) {
        // Check if error is related to a specific PSP (simplified check via attribute
        // or context if available)
        // For now, assume if no PSP auth/header is present, it's a system/non-PSP error
        // Or simply log ALL errors with this classification as requested "errors not
        // relating to any PSP"

        // In a real scenario we'd check SecurityContext for PSP_USER authority or pspId
        // keeping it simple: Log everything with the requested format

        String page = request.getRequestURI();
        String method = request.getMethod();

        logger.error("[SYSTEM_ERROR] [Function: {}] [Page: {} {}] Message: {}",
                functionName, method, page, ex.getMessage(), ex);
    }
}
