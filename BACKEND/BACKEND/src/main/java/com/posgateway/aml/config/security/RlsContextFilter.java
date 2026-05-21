package com.posgateway.aml.config.security;

import com.posgateway.aml.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Sets PostgreSQL session variable `app.current_psp_id` for every authenticated request.
 * This powers the Row Level Security policies in V138.
 */
@Component
public class RlsContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RlsContextFilter.class);

    private final DataSource dataSource;

    public RlsContextFilter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof User user) {
            Long pspId = (user.getPsp() != null) ? user.getPsp().getPspId() : null;

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                if (pspId != null) {
                    stmt.execute("SET app.current_psp_id = " + pspId);
                } else {
                    stmt.execute("SET app.current_psp_id = NULL");
                }

            } catch (Exception e) {
                log.warn("Failed to set RLS context variable: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}