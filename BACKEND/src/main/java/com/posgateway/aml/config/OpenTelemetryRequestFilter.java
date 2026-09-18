package com.posgateway.aml.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Creates one server span per API request and returns its trace id to the caller. */
@Component
@ConditionalOnBean(OpenTelemetry.class)
public class OpenTelemetryRequestFilter extends OncePerRequestFilter {

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryRequestFilter(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Span span = openTelemetry.getTracer("com.posgateway.aml.http")
                .spanBuilder(request.getMethod() + " " + request.getRequestURI())
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        span.setAttribute("http.request.method", request.getMethod());
        span.setAttribute("url.path", request.getRequestURI());
        String traceId = span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : null;
        if (traceId != null) {
            response.setHeader("X-Trace-Id", traceId);
        }
        try (Scope ignored = span.makeCurrent()) {
            filterChain.doFilter(request, response);
            span.setAttribute("http.response.status_code", response.getStatus());
            if (response.getStatus() >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
        } catch (Exception failure) {
            span.recordException(failure);
            span.setStatus(StatusCode.ERROR);
            throw failure;
        } finally {
            span.end();
        }
    }
}
