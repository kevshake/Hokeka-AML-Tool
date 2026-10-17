package com.posgateway.aml.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Contract §7.14 / §7.16: responses of the tunnel-backed endpoints MUST carry
 * {@code Cache-Control: no-store} and MUST NOT be served from any cache. Set before the request is
 * handled, so success bodies, error bodies and streamed exports all carry it (Spring Security's own
 * cache headers do not overwrite a value that is already present).
 *
 * <p>Covers {@code /transactions} (list, {@code /{id}}, {@code /{id}/…}, {@code :batchGet},
 * {@code /export}), {@code /edge-data/…}, and the workflow views that embed pulled details:
 * alert detail, SAR detail, case timeline and case network.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class EdgeDataNoStoreFilter extends OncePerRequestFilter {

    /** Path prefixes (after the servlet context path) whose responses must never be cached. */
    static final List<String> PREFIXES = List.of("/transactions", "/edge-data/");

    /** Workflow views that embed live-pulled transaction details. */
    private static final List<java.util.regex.Pattern> VIEWS = List.of(
            java.util.regex.Pattern.compile("^/alerts/[0-9]+$"),
            java.util.regex.Pattern.compile("^/compliance/sar/[0-9]+/detail$"),
            java.util.regex.Pattern.compile("^/cases/[0-9]+/(timeline|graph|network)$"));

    /** Adds the headers for matching paths. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (matches(path(request))) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
        }
        chain.doFilter(request, response);
    }

    /** The request path below the context path. */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }

    /** True for the tunnel-backed endpoints and the detail-carrying workflow views. */
    static boolean matches(String path) {
        for (String p : PREFIXES) {
            if (path.startsWith(p)) {
                return true;
            }
        }
        for (java.util.regex.Pattern v : VIEWS) {
            if (v.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }
}
