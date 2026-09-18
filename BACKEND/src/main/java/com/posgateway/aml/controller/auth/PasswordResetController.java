package com.posgateway.aml.controller.auth;

import com.posgateway.aml.dto.auth.EmergencyPasswordResetRequest;
import com.posgateway.aml.dto.auth.PasswordResetConfirmRequest;
import com.posgateway.aml.dto.auth.PasswordResetRequest;
import com.posgateway.aml.service.auth.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * Request a password reset link. Always returns 200 to avoid user enumeration.
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestReset(@RequestBody PasswordResetRequest req,
            HttpServletRequest request) {
        passwordResetService.requestPasswordReset(
                req != null ? req.getIdentifier() : null,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of(
                "message", "If the account exists, a reset link has been sent."
        ));
    }

    /**
     * Confirm password reset using a one-time token. Resets password to default password.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmReset(@RequestBody PasswordResetConfirmRequest req,
            HttpServletRequest request) {
        passwordResetService.confirmPasswordReset(
                req != null ? req.getToken() : null,
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of(
                "message", "Password reset successful. Your password has been reset to the default password. Please check your email for details."
        ));
    }

    /**
     * Emergency reset (TEMP PASSWORD) for super-user recovery.
     *
     * Disabled by default. Requires server-side enable flag + secret header and (optionally) localhost.
     */
    @PostMapping("/emergency")
    public ResponseEntity<Map<String, Object>> emergencyReset(@RequestBody EmergencyPasswordResetRequest req,
            @RequestHeader(value = "X-EMERGENCY-RESET-SECRET", required = false) String secret,
            HttpServletRequest request) {
        try {
            passwordResetService.emergencyReset(
                    req != null ? req.getIdentifier() : null,
                    secret,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );
            return ResponseEntity.ok(Map.of(
                    "message", "If the account exists, the password has been reset to the default password. Please check your email for details."
            ));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "forbidden",
                    "message", ex.getMessage()
            ));
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "bad_request",
                "message", ex.getMessage() != null ? ex.getMessage() : "Invalid request"
        ));
    }
}


