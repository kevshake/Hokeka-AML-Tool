package com.posgateway.aml.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Session Management Controller
 * Handles session refresh, validation, and timeout management
 */
@RestController
@RequestMapping("/auth/session")
public class SessionController {

    @Value("${server.servlet.session.timeout:1800}") // Default 30 minutes in seconds
    private int sessionTimeoutSeconds;

    @Value("${server.servlet.session.cookie.max-age:1800}")
    private int cookieMaxAge;

    /**
     * Check session validity and return remaining time
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session == null) {
            response.put("valid", false);
            response.put("message", "No active session");
            return ResponseEntity.status(401).body(response);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            response.put("valid", false);
            response.put("message", "Not authenticated");
            return ResponseEntity.status(401).body(response);
        }

        long lastAccessedTime = session.getLastAccessedTime();
        long currentTime = System.currentTimeMillis();
        long maxInactiveInterval = session.getMaxInactiveInterval() * 1000L; // Convert to milliseconds
        long timeRemaining = maxInactiveInterval - (currentTime - lastAccessedTime);

        response.put("valid", true);
        response.put("timeRemaining", Math.max(0, timeRemaining / 1000)); // Return in seconds
        response.put("maxInactiveInterval", session.getMaxInactiveInterval());
        response.put("lastAccessedTime", lastAccessedTime);
        response.put("username", auth.getName());

        return ResponseEntity.ok(response);
    }

    /**
     * Refresh session - extends session timeout
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session == null) {
            response.put("success", false);
            response.put("message", "No active session");
            return ResponseEntity.status(401).body(response);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            response.put("success", false);
            response.put("message", "Not authenticated");
            return ResponseEntity.status(401).body(response);
        }

        // Touch the session to update last accessed time
        session.setAttribute("lastRefresh", System.currentTimeMillis());
        
        // Set session timeout
        session.setMaxInactiveInterval(sessionTimeoutSeconds);

        long lastAccessedTime = session.getLastAccessedTime();
        long currentTime = System.currentTimeMillis();
        long maxInactiveInterval = session.getMaxInactiveInterval() * 1000L;
        long timeRemaining = maxInactiveInterval - (currentTime - lastAccessedTime);

        response.put("success", true);
        response.put("message", "Session refreshed");
        response.put("sessionTimeout", sessionTimeoutSeconds);
        response.put("timeRemaining", Math.max(0, timeRemaining / 1000));
        response.put("username", auth.getName());

        return ResponseEntity.ok(response);
    }

    /**
     * Get session information
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSessionInfo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session == null) {
            response.put("active", false);
            return ResponseEntity.ok(response);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        response.put("active", true);
        response.put("sessionId", session.getId());
        response.put("maxInactiveInterval", session.getMaxInactiveInterval());
        response.put("createdAt", session.getCreationTime());
        response.put("lastAccessedAt", session.getLastAccessedTime());
        response.put("username", auth != null ? auth.getName() : "anonymous");

        return ResponseEntity.ok(response);
    }

    /**
     * Invalidate current session
     */
    @PostMapping("/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session != null) {
            session.invalidate();
            response.put("success", true);
            response.put("message", "Session invalidated");
        } else {
            response.put("success", false);
            response.put("message", "No active session");
        }

        return ResponseEntity.ok(response);
    }
}

