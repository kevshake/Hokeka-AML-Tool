package com.posgateway.aml.service.auth;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.auth.PasswordResetToken;
import com.posgateway.aml.repository.PasswordResetTokenRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Value("${auth.password-reset.token-ttl-minutes:15}")
    private int tokenTtlMinutes;

    @Value("${auth.password-reset.pepper:change-me-in-production}")
    private String tokenPepper;

    @Value("${auth.emergency-reset.enabled:false}")
    private boolean emergencyEnabled;

    @Value("${auth.emergency-reset.secret:}")
    private String emergencySecret;

    @Value("${auth.emergency-reset.local-only:true}")
    private boolean emergencyLocalOnly;

    @Value("${auth.default-password:password123}")
    private String defaultPassword;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    /**
     * Request password reset for a user (username/email). Always succeeds without revealing
     * whether the user exists.
     */
    @Transactional
    public void requestPasswordReset(String identifier, String ipAddress, String userAgent) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return;
        }

        String trimmed = identifier.trim();
        Optional<User> userOpt = findUserByIdentifier(trimmed);
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();
        if (!user.isEnabled()) {
            return;
        }

        // Invalidate any existing active tokens for this user
        List<PasswordResetToken> activeTokens = tokenRepository.findByUserAndUsedAtIsNull(user);
        if (!activeTokens.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (PasswordResetToken t : activeTokens) {
                t.setUsedAt(now);
            }
            tokenRepository.saveAll(activeTokens);
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(Math.max(5, tokenTtlMinutes)));
        token.setRequestedIp(ipAddress);
        token.setRequestedUserAgent(userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 512)) : null);

        tokenRepository.save(token);

        String resetLink = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/password-reset.html")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        // Send reset link to the user's email (mock email logs for now)
        notificationService.sendEmail(
                user.getEmail(),
                "Password Reset Request",
                "A password reset was requested for your account.\n\n" +
                        "Reset link (valid for ~" + tokenTtlMinutes + " minutes):\n" +
                        resetLink + "\n\n" +
                        "If you did not request this, you can ignore this email."
        );

        auditLogService.logAction(null, "PASSWORD_RESET_REQUEST", "USER",
                user.getId() != null ? user.getId().toString() : "UNKNOWN",
                null, null, ipAddress, "Password reset requested (link sent)");
    }

    /**
     * Confirm password reset using token. Resets password to default password.
     */
    @Transactional
    public void confirmPasswordReset(String rawToken, String ipAddress) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid reset token");
        }

        String tokenHash = hashToken(rawToken.trim());
        PasswordResetToken token = tokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        User user = token.getUser();
        if (user == null) {
            token.setUsedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        // Reset to default password
        String defaultPwd = defaultPassword != null && !defaultPassword.isBlank() ? defaultPassword : "password123";
        user.setPasswordHash(passwordEncoder.encode(defaultPwd));
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        notificationService.sendEmail(
                user.getEmail(),
                "Password Reset Successful",
                "Your password has been reset to the default password.\n\n" +
                        "Default password: " + defaultPwd + "\n\n" +
                        "Please log in and change your password immediately for security.\n" +
                        "If you did not request this reset, contact support immediately."
        );

        auditLogService.logAction(null, "PASSWORD_RESET_CONFIRM", "USER",
                user.getId() != null ? user.getId().toString() : "UNKNOWN",
                null, null, ipAddress, "Password reset confirmed (reset to default password)");
    }

    /**
     * Emergency reset: generates a temporary password and sends it to the user's email.
     * This endpoint must be explicitly enabled + protected by a server-side secret.
     */
    @Transactional
    public void emergencyReset(String identifier, String providedSecret, String ipAddress, String userAgent) {
        if (!emergencyEnabled) {
            throw new AccessDeniedException("Emergency reset is disabled");
        }
        if (emergencySecret == null || emergencySecret.isBlank()) {
            throw new AccessDeniedException("Emergency reset secret not configured");
        }
        if (providedSecret == null || !constantTimeEquals(emergencySecret, providedSecret)) {
            throw new AccessDeniedException("Invalid emergency reset secret");
        }
        if (emergencyLocalOnly && !isLoopback(ipAddress)) {
            throw new AccessDeniedException("Emergency reset is restricted to localhost");
        }

        if (identifier == null || identifier.trim().isEmpty()) {
            return;
        }

        Optional<User> userOpt = findUserByIdentifier(identifier.trim());
        if (userOpt.isEmpty()) {
            // Avoid user enumeration even on emergency route (still return success)
            return;
        }

        User user = userOpt.get();
        if (!user.isEnabled()) {
            return;
        }

        // Invalidate any existing active tokens for this user
        List<PasswordResetToken> activeTokens = tokenRepository.findByUserAndUsedAtIsNull(user);
        if (!activeTokens.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (PasswordResetToken t : activeTokens) {
                t.setUsedAt(now);
            }
            tokenRepository.saveAll(activeTokens);
        }

        // Reset to default password (same as regular reset)
        String defaultPwd = defaultPassword != null && !defaultPassword.isBlank() ? defaultPassword : "password123";
        user.setPasswordHash(passwordEncoder.encode(defaultPwd));
        userRepository.save(user);

        notificationService.sendEmail(
                user.getEmail(),
                "Emergency Password Reset",
                "An emergency password reset was performed for your account.\n\n" +
                        "Your password has been reset to the default password.\n" +
                        "Default password: " + defaultPwd + "\n\n" +
                        "Please log in and change your password immediately for security.\n" +
                        "If you did not request this, contact support immediately."
        );

        auditLogService.logAction(null, "EMERGENCY_PASSWORD_RESET", "USER",
                user.getId() != null ? user.getId().toString() : "UNKNOWN",
                null, null, ipAddress, "Emergency reset executed (reset to default password)");

        log.warn("Emergency password reset executed for user={}, ip={}, ua={}",
                user.getUsername(), ipAddress, userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 120)) : null);
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        // identifier can be username OR email (usernames may also look like emails)
        Optional<User> byEmail = userRepository.findByEmail(identifier);
        if (byEmail.isPresent())
            return byEmail;
        return userRepository.findByUsername(identifier);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(tokenPepper.getBytes(StandardCharsets.UTF_8));
            md.update((byte) ':');
            md.update(rawToken.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private boolean isLoopback(String ip) {
        if (ip == null) return false;
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

}


