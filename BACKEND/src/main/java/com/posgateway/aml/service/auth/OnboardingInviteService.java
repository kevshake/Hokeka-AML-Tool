package com.posgateway.aml.service.auth;

import com.posgateway.aml.entity.auth.OnboardingInvite;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.auth.OnboardingInviteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class OnboardingInviteService {
    private final OnboardingInviteRepository repository;
    private final PspRepository pspRepository;

    public OnboardingInviteService(OnboardingInviteRepository repository, PspRepository pspRepository) {
        this.repository = repository;
        this.pspRepository = pspRepository;
    }

    @Transactional
    public IssuedInvite issue(Long pspId, String role, LocalDateTime expiresAt, Long createdBy) {
        if (role == null || role.isBlank() || expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("role and a future expiresAt are required");
        }
        Psp psp = pspId == null ? null : pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));
        String plaintext = UUID.randomUUID() + "." + UUID.randomUUID();
        OnboardingInvite invite = new OnboardingInvite();
        invite.setTokenHash(sha256(plaintext));
        invite.setPsp(psp);
        invite.setRole(role.trim().toUpperCase());
        invite.setExpiresAt(expiresAt);
        invite.setCreatedBy(createdBy);
        OnboardingInvite saved = repository.save(invite);
        return new IssuedInvite(saved.getId(), plaintext, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<OnboardingInvite> validate(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) return Optional.empty();
        return repository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                sha256(plaintextToken), LocalDateTime.now());
    }

    @Transactional
    public OnboardingInvite consume(String plaintextToken) {
        OnboardingInvite invite = validate(plaintextToken)
                .orElseThrow(() -> new IllegalArgumentException("Invite is invalid, expired, or already used"));
        invite.setUsedAt(LocalDateTime.now());
        return repository.save(invite);
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record IssuedInvite(Long inviteId, String token, LocalDateTime expiresAt) {}
}
