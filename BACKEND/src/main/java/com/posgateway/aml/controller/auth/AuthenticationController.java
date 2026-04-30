package com.posgateway.aml.controller.auth;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;

/**
 * Authentication Controller
 * Handles login and user session management with strict session isolation
 * 
 * Security Features:
 * - Session isolation per user
 * - Session fixation protection
 * - Cross-user data access prevention
 * - Complete session cleanup on logout
 */
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);
    
    // Session attribute keys
    private static final String SESSION_USER_ID_KEY = "SESSION_USER_ID";
    private static final String SESSION_USERNAME_KEY = "SESSION_USERNAME";

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    /**
     * Login endpoint
     * POST /api/v1/auth/login
     * 
     * Security measures:
     * - Invalidates any existing session to prevent session fixation
     * - Creates new session with user-specific attributes
     * - Stores user ID and username in session for validation
     * - Ensures session isolation per user
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody(required = false) Map<String, String> credentials,
            HttpServletRequest request) {
        
        // Validate request body
        if (credentials == null || credentials.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Username and password are required");
            return ResponseEntity.status(400).body(error);
        }
        
        String username = credentials.get("username");
        String password = credentials.get("password");

        // Validate credentials are provided
        if (username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Username and password are required");
            return ResponseEntity.status(400).body(error);
        }

        username = username.trim();

        try {
            // CRITICAL: Invalidate any existing session to prevent session fixation
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                logger.info("Invalidating existing session {} for new login by user {}", 
                    existingSession.getId(), username);
                clearSessionAttributes(existingSession);
                existingSession.invalidate();
            }

            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            // Get user details BEFORE creating session
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

            // Validate user is enabled
            if (!user.isEnabled()) {
                logger.warn("Login attempt for disabled user: {}", username);
                Map<String, Object> error = new HashMap<>();
                error.put("message", "User account is disabled");
                return ResponseEntity.status(403).body(error);
            }

            // Create NEW session (session fixation protection via SecurityConfig)
            HttpSession session = request.getSession(true);
            
            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Store security context in session
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
            );

            // CRITICAL: Store user-specific session attributes for validation
            // This ensures we can verify session ownership later
            session.setAttribute(SESSION_USER_ID_KEY, user.getId());
            session.setAttribute(SESSION_USERNAME_KEY, user.getUsername());
            
            // Set session timeout (30 minutes default)
            session.setMaxInactiveInterval(1800);

            logger.info("User {} successfully logged in with session {}", username, session.getId());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", session.getId()); // Use session ID as token
            response.put("user", buildUserResponse(user));
            response.put("redirectUrl", "/dashboard"); // Indicate where to redirect after login

            return ResponseEntity.ok(response);

        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            logger.warn("Failed login attempt for user: {}", username);
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Invalid username or password");
            return ResponseEntity.status(401).body(error);
        } catch (org.springframework.security.authentication.DisabledException e) {
            logger.warn("Login attempt for disabled user: {}", username);
            Map<String, Object> error = new HashMap<>();
            error.put("message", "User account is disabled");
            return ResponseEntity.status(403).body(error);
        } catch (Exception e) {
            logger.error("Login error for user {}: {}", username, e.getMessage(), e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Login failed. Please try again.");
            return ResponseEntity.status(401).body(error);
        }
    }

    /**
     * Get current user
     * GET /api/v1/auth/me
     * 
     * Security measures:
     * - Validates authentication from SecurityContext
     * - Validates session ownership (user ID in session matches authenticated user)
     * - Prevents cross-user data access
     * - Only returns data for the authenticated user
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            logger.warn("Unauthenticated access attempt to /me endpoint");
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Not authenticated");
            return ResponseEntity.status(401).body(error);
        }

        try {
            String username = authentication.getName();
            
            // CRITICAL: Validate session ownership to prevent cross-user data access
            HttpSession session = request.getSession(false);
            if (session != null) {
                Long sessionUserId = (Long) session.getAttribute(SESSION_USER_ID_KEY);
                String sessionUsername = (String) session.getAttribute(SESSION_USERNAME_KEY);
                
                // Get user from database
                User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

                // Validate session belongs to this user
                if (sessionUserId != null && !sessionUserId.equals(user.getId())) {
                    logger.error("SECURITY ALERT: Session user ID mismatch! Session user ID: {}, Authenticated user ID: {}, Username: {}", 
                        sessionUserId, user.getId(), username);
                    // Clear the compromised session
                    clearSessionAttributes(session);
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                    
                    Map<String, Object> error = new HashMap<>();
                    error.put("message", "Session validation failed");
                    return ResponseEntity.status(401).body(error);
                }
                
                // Validate session username matches authenticated username
                if (sessionUsername != null && !sessionUsername.equals(username)) {
                    logger.error("SECURITY ALERT: Session username mismatch! Session username: {}, Authenticated username: {}", 
                        sessionUsername, username);
                    clearSessionAttributes(session);
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                    
                    Map<String, Object> error = new HashMap<>();
                    error.put("message", "Session validation failed");
                    return ResponseEntity.status(401).body(error);
                }
                
                // Session is valid - return user data
                return ResponseEntity.ok(buildUserResponse(user));
            } else {
                // No session but authenticated - this shouldn't happen with session-based auth
                logger.warn("Authenticated user {} has no session", username);
                User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
                return ResponseEntity.ok(buildUserResponse(user));
            }
        } catch (Exception e) {
            logger.error("Error getting current user: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("message", "User not found");
            return ResponseEntity.status(404).body(error);
        }
    }

    /**
     * Logout endpoint
     * POST /api/v1/auth/logout
     * 
     * Security measures:
     * - Clears all session attributes before invalidation
     * - Clears SecurityContext
     * - Logs logout event for audit
     * - Ensures complete session cleanup
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String username = null;
        String sessionId = null;
        
        try {
            // Get user info before clearing session
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getPrincipal())) {
                username = authentication.getName();
            }
            
            HttpSession session = request.getSession(false);
            if (session != null) {
                sessionId = session.getId();
                
                // CRITICAL: Clear all session attributes before invalidation
                // This prevents any data leakage
                clearSessionAttributes(session);
                
                // Invalidate session
                session.invalidate();
                
                logger.info("User {} logged out from session {}", username != null ? username : "unknown", sessionId);
            }
        } catch (Exception e) {
            logger.error("Error during logout: {}", e.getMessage(), e);
        } finally {
            // Always clear SecurityContext, even if session handling fails
            SecurityContextHolder.clearContext();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Clear all session attributes
     * This ensures no user data remains in the session
     */
    private void clearSessionAttributes(HttpSession session) {
        if (session == null) {
            return;
        }
        
        try {
            Enumeration<String> attributeNames = session.getAttributeNames();
            while (attributeNames.hasMoreElements()) {
                String attributeName = attributeNames.nextElement();
                session.removeAttribute(attributeName);
            }
        } catch (IllegalStateException e) {
            // Session already invalidated - ignore
            logger.debug("Session already invalidated while clearing attributes");
        }
    }

    /**
     * Build user response object
     */
    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("email", user.getEmail());
        userMap.put("firstName", user.getFirstName());
        userMap.put("lastName", user.getLastName());
        userMap.put("enabled", user.isEnabled());
        userMap.put("createdAt", user.getCreatedAt());

        // Role information
        if (user.getRole() != null) {
            Map<String, Object> roleMap = new HashMap<>();
            roleMap.put("id", user.getRole().getId());
            roleMap.put("name", user.getRole().getName());
            roleMap.put("permissions", user.getRole().getPermissions());
            userMap.put("role", roleMap);
        }

        // PSP information - All users must have a PSP (PSP ID 0 for Super Admin)
        if (user.getPsp() != null) {
            Map<String, Object> pspMap = new HashMap<>();
            pspMap.put("id", user.getPsp().getPspId());
            pspMap.put("name", user.getPsp().getTradingName());
            pspMap.put("code", user.getPsp().getPspCode());
            
            // Include PSP theme configuration
            Map<String, Object> themeMap = new HashMap<>();
            themeMap.put("brandingTheme", user.getPsp().getBrandingTheme() != null ? user.getPsp().getBrandingTheme() : "default");
            themeMap.put("primaryColor", user.getPsp().getPrimaryColor());
            themeMap.put("secondaryColor", user.getPsp().getSecondaryColor());
            themeMap.put("accentColor", user.getPsp().getAccentColor());
            themeMap.put("logoUrl", user.getPsp().getLogoUrl());
            themeMap.put("fontFamily", user.getPsp().getFontFamily());
            themeMap.put("fontSize", user.getPsp().getFontSize());
            themeMap.put("buttonRadius", user.getPsp().getButtonRadius());
            themeMap.put("buttonStyle", user.getPsp().getButtonStyle());
            themeMap.put("navStyle", user.getPsp().getNavStyle());
            pspMap.put("theme", themeMap);
            
            userMap.put("psp", pspMap);
            // Always include psp_id at root level for consistency
            userMap.put("pspId", user.getPsp().getPspId());
        } else {
            // Fallback: should not happen after migration, but handle gracefully
            userMap.put("psp", null);
            userMap.put("pspId", 0L); // Default to Super Admin PSP ID
        }

        return userMap;
    }
}
