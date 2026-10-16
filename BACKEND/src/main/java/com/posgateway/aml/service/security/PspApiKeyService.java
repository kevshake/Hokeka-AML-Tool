package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.security.PspApiKey;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.security.PspApiKeyRepository;
import com.posgateway.aml.service.auth.OnboardingInviteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Issues, rotates, revokes and verifies PSP machine API keys ({@code hka_…}).
 *
 * <p>Only the SHA-256 hash of a key is stored. {@link #authenticate(String)} hashes the presented
 * key, looks the row up by that hash, and re-compares the hashes with
 * {@link MessageDigest#isEqual(byte[], byte[])} (constant time). A key authenticates only while it is
 * not revoked, not expired, and its PSP is {@code ACTIVE}.
 */
@Service
public class PspApiKeyService {

    /** Prefix of every issued key; anything else is rejected without a database lookup. */
    public static final String KEY_PREFIX = "hka_";
    /** Upper bound on a presented key; issued keys are 47 characters. */
    static final int MAX_KEY_LENGTH = 128;

    private final PspApiKeyRepository repository;
    private final PspRepository pspRepository;
    private final SecureRandom random = new SecureRandom();
    private final int defaultTtlDays;

    /** Convenience constructor (no default expiry), used by tests. */
    public PspApiKeyService(PspApiKeyRepository repository, PspRepository pspRepository) {
        this(repository, pspRepository, 0);
    }

    @Autowired
    public PspApiKeyService(PspApiKeyRepository repository, PspRepository pspRepository,
                            @Value("${hokeka.api-keys.default-ttl-days:0}") int defaultTtlDays) {
        this.repository = repository;
        this.pspRepository = pspRepository;
        this.defaultTtlDays = Math.max(0, defaultTtlDays);
    }

    /** Keys of one PSP, newest first (hash never serialised). */
    public List<PspApiKey> list(Long pspId) {
        return repository.findByPspPspIdOrderByCreatedAtDesc(pspId);
    }

    /**
     * Issues a new key for the PSP. The plaintext is returned exactly once; only its hash is stored.
     * When {@code hokeka.api-keys.default-ttl-days > 0} the key expires after that many days.
     */
    @Transactional
    public IssuedApiKey create(Long pspId, Long createdBy) {
        String plaintext = KEY_PREFIX + randomToken();
        PspApiKey row = new PspApiKey();
        row.setPsp(pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found")));
        row.setKeyPrefix(plaintext.substring(0, Math.min(12, plaintext.length())));
        row.setKeyHash(OnboardingInviteService.sha256(plaintext));
        row.setCreatedBy(createdBy);
        if (defaultTtlDays > 0) {
            row.setExpiresAt(LocalDateTime.now().plusDays(defaultTtlDays));
        }
        PspApiKey saved = repository.save(row);
        return new IssuedApiKey(saved.getId(), saved.getKeyPrefix(), plaintext);
    }

    /** Revokes the given key immediately and issues a replacement for the same PSP. */
    @Transactional
    public IssuedApiKey rotate(Long id, Long createdBy) {
        PspApiKey old = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        old.setRotatedAt(LocalDateTime.now());
        old.setRevokedAt(LocalDateTime.now());
        repository.save(old);
        return create(old.getPsp().getPspId(), createdBy);
    }

    /** Revokes the key; it stops authenticating on the next request (no caching). */
    @Transactional
    public void revoke(Long id) {
        PspApiKey row = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        row.setRevokedAt(LocalDateTime.now());
        repository.save(row);
    }

    /**
     * Verifies a presented key.
     *
     * @param presented the raw key from {@code X-Api-Key} or {@code Authorization: Bearer}
     * @return the key id, prefix and owning PSP when the key is valid, usable and its PSP is ACTIVE
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedApiKey> authenticate(String presented) {
        if (presented == null) {
            return Optional.empty();
        }
        String key = presented.trim();
        if (!key.startsWith(KEY_PREFIX) || key.length() > MAX_KEY_LENGTH) {
            return Optional.empty();
        }
        String hash = OnboardingInviteService.sha256(key);
        Optional<PspApiKey> row = repository.findByKeyHashWithPsp(hash);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        PspApiKey stored = row.get();
        if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                stored.getKeyHash().getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        if (!stored.isUsableAt(LocalDateTime.now())) {
            return Optional.empty();
        }
        Psp psp = stored.getPsp();
        if (psp == null || !"ACTIVE".equalsIgnoreCase(psp.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedApiKey(stored.getId(), stored.getKeyPrefix(), psp));
    }

    /** 32 random bytes, URL-safe Base64 without padding. */
    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** One-time view of a freshly issued key. */
    public record IssuedApiKey(Long id, String keyPrefix, String plaintextKey) {}

    /** A verified key: which key it was and the PSP it is bound to. */
    public record AuthenticatedApiKey(Long keyId, String keyPrefix, Psp psp) {}
}
