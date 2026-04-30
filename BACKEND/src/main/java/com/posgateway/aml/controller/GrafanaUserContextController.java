package com.posgateway.aml.controller;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grafana User Context Controller
 * Provides user context information for Grafana dashboards
 * Used for role-based access control and PSP filtering
 */
@RestController
@RequestMapping("/grafana")
public class GrafanaUserContextController {

    private final UserRepository userRepository;

    @Autowired
    public GrafanaUserContextController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Get user context for Grafana dashboards
     * Returns PSP code and user role for dashboard filtering
     * 
     * @return User context with psp_code and user_role
     */
    @GetMapping("/user-context")
    public ResponseEntity<Map<String, String>> getUserContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.ok(createAnonymousContext());
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        
        if (user == null) {
            return ResponseEntity.ok(createAnonymousContext());
        }

        Map<String, String> context = new HashMap<>();
        
        // All users must have a PSP (PSP ID 0 for Super Admin)
        Long pspId = user.getPsp() != null ? user.getPsp().getPspId() : 0L;
        String pspCode = user.getPsp() != null ? user.getPsp().getPspCode() : "SYSTEM_ADMIN";
        
        // Check if user is Platform Administrator (PSP ID 0)
        boolean isPlatformAdmin = user.getRole() != null && 
                ("ADMIN".equals(user.getRole().getName()) || 
                 "APP_CONTROLLER".equals(user.getRole().getName())) &&
                pspId != null && pspId == 0L;
        
        if (isPlatformAdmin) {
            // Platform Administrator (PSP ID 0) - can see all PSPs
            context.put("user_role", "PLATFORM_ADMIN");
            context.put("psp_code", "ALL");
            context.put("psp_id", "0"); // Super Admin PSP ID
            context.put("can_view_all_psps", "true");
        } else {
            // PSP User - can only see their PSP
            context.put("user_role", "PSP_USER");
            context.put("psp_code", pspCode != null ? pspCode : "UNKNOWN");
            context.put("psp_id", pspId != null ? pspId.toString() : "0");
            context.put("can_view_all_psps", "false");
        }
        
        return ResponseEntity.ok(context);
    }

    /**
     * Get available dashboards for the current user
     * Returns dashboards filtered by user role and PSP access
     * Follows the recommended pattern: backend-driven dashboard menu
     * 
     * @return List of available dashboards with metadata
     */
    @GetMapping("/dashboards")
    public ResponseEntity<List<Map<String, Object>>> getDashboards() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        
        if (user == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        // Check if user is system admin (PSP ID 0 and has admin permissions)
        Long pspId = user.getPsp() != null ? user.getPsp().getPspId() : 0L;
        boolean isSystemAdmin = user.getRole() != null && 
                (user.getRole().getName().equals("ADMIN") || 
                 user.getRole().getName().equals("APP_CONTROLLER")) &&
                pspId != null && pspId == 0L;

        List<Map<String, Object>> dashboards = new ArrayList<>();

        // AML/Business Dashboards (available to all users)
        dashboards.add(createDashboard("TRANSACTION OVERVIEW", "transaction-overview", "/dashboard/transactions", false));
        dashboards.add(createDashboard("AML RISK", "aml-risk", "/dashboard/aml-risk", false));
        dashboards.add(createDashboard("FRAUD DETECTION", "fraud-detection", "/dashboard/fraud", false));
        dashboards.add(createDashboard("COMPLIANCE", "compliance", "/dashboard/compliance", false));
        dashboards.add(createDashboard("MODEL PERFORMANCE", "model-performance", "/dashboard/models", false));
        dashboards.add(createDashboard("SCREENING", "screening", "/dashboard/screening", false));

        // System Performance Dashboards (system admins only)
        if (isSystemAdmin) {
            dashboards.add(createDashboard("SYSTEM PERFORMANCE", "system-performance", "/dashboard/system", true));
            dashboards.add(createDashboard("INFRASTRUCTURE", "infrastructure-resources", "/dashboard/infrastructure", true));
            dashboards.add(createDashboard("THREAD POOLS", "thread-pools-throughput", "/dashboard/threads", true));
            dashboards.add(createDashboard("CIRCUIT BREAKER", "circuit-breaker-resilience", "/dashboard/circuit-breaker", true));
        }

        return ResponseEntity.ok(dashboards);
    }

    /**
     * Create dashboard metadata map
     */
    private Map<String, Object> createDashboard(String menu, String uid, String path, boolean systemOnly) {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("menu", menu);
        dashboard.put("uid", uid);
        dashboard.put("path", path);
        dashboard.put("systemOnly", systemOnly);
        return dashboard;
    }

    /**
     * Create anonymous context for unauthenticated users
     */
    private Map<String, String> createAnonymousContext() {
        Map<String, String> context = new HashMap<>();
        context.put("user_role", "ANONYMOUS");
        context.put("psp_code", "NONE");
        context.put("psp_id", "0");
        context.put("can_view_all_psps", "false");
        return context;
    }
}