package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.security.PspApiKey;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.security.PspApiKeyRepository;
import com.posgateway.aml.service.auth.OnboardingInviteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class PspApiKeyService {
    private final PspApiKeyRepository repository;
    private final PspRepository pspRepository;
    private final SecureRandom random = new SecureRandom();

    public PspApiKeyService(PspApiKeyRepository repository, PspRepository pspRepository) {
        this.repository = repository;
        this.pspRepository = pspRepository;
    }

    public List<PspApiKey> list(Long pspId) {
        return repository.findByPspPspIdOrderByCreatedAtDesc(pspId);
    }

    @Transactional
    public IssuedApiKey create(Long pspId, Long createdBy) {
        String plaintext = "hka_" + randomToken();
        PspApiKey row = new PspApiKey();
        row.setPsp(pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found")));
        row.setKeyPrefix(plaintext.substring(0, Math.min(12, plaintext.length())));
        row.setKeyHash(OnboardingInviteService.sha256(plaintext));
        row.setCreatedBy(createdBy);
        PspApiKey saved = repository.save(row);
        return new IssuedApiKey(saved.getId(), saved.getKeyPrefix(), plaintext);
    }

    @Transactional
    public IssuedApiKey rotate(Long id, Long createdBy) {
        PspApiKey old = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        old.setRotatedAt(LocalDateTime.now());
        old.setRevokedAt(LocalDateTime.now());
        repository.save(old);
        return create(old.getPsp().getPspId(), createdBy);
    }

    @Transactional
    public void revoke(Long id) {
        PspApiKey row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        row.setRevokedAt(LocalDateTime.now());
        repository.save(row);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedApiKey(Long id, String keyPrefix, String plaintextKey) {}
}
