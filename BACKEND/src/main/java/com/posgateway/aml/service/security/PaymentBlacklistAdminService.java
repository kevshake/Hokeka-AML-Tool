package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.security.PaymentBlacklistEntry;
import com.posgateway.aml.repository.security.PaymentBlacklistRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.cache.FeatureCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Management (CRUD) of the payment blacklist with the V250 tenancy model:
 * <ul>
 *   <li>an entry belongs to one PSP ({@code psp_id}), or is platform-wide
 *       ({@code psp_id IS NULL AND platform_wide = true}) and then applies to every PSP;</li>
 *   <li>PSP users list their PSP's entries plus the platform-wide list (read-only for them) and may
 *       only create / change / remove entries of their own PSP;</li>
 *   <li>platform administrators manage every entry, including the platform-wide list.</li>
 * </ul>
 * Removal is a soft delete ({@code active=false}) so the history stays auditable; re-adding a removed
 * value re-activates the same row (the V250 unique indexes cover inactive rows too). Every change is
 * written to the audit log with before/after state, and the Redis blacklist cache is kept in step.
 * PAN entries are never stored raw: a PAN is converted to the same lookup hash as
 * {@code transactions.pan_hash} ({@link PiiLookupHasher#hashPan}).
 */
@Service
public class PaymentBlacklistAdminService {

    private static final Logger log = LoggerFactory.getLogger(PaymentBlacklistAdminService.class);

    /** Entry types the decision engine and rule enrichment actually check. */
    public static final Set<String> TYPES = Set.of("pan", "terminal", "ip", "device", "payment_details", "wallet");

    private static final Pattern RAW_PAN = Pattern.compile("\\d{12,19}");
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-fA-F]{64}");

    private final PaymentBlacklistRepository repository;
    private final FeatureCacheService cache;
    private final PspIsolationService isolation;
    private final PiiLookupHasher hasher;
    private final AuditLogService audit;

    public PaymentBlacklistAdminService(PaymentBlacklistRepository repository, FeatureCacheService cache,
                                        PspIsolationService isolation, PiiLookupHasher hasher,
                                        AuditLogService audit) {
        this.repository = repository;
        this.cache = cache;
        this.isolation = isolation;
        this.hasher = hasher;
        this.audit = audit;
    }

    /** Create request: {@code pspId} is honoured for platform admins only; PSP users always write to their PSP. */
    public record CreateRequest(String entryType, String value, String reason, LocalDateTime expiresAt,
                                Long pspId, Boolean platformWide) {}

    /** Update request: any null field is left unchanged. */
    public record UpdateRequest(String reason, LocalDateTime expiresAt, Boolean clearExpiry, Boolean active) {}

    /** Lists entries visible to the caller. Platform admins may filter by PSP / platform-wide. */
    @Transactional(readOnly = true)
    public Page<PaymentBlacklistEntry> list(Long pspIdFilter, Boolean platformWideFilter, String type,
                                            Boolean active, Pageable pageable) {
        User caller = requireCaller();
        String normalizedType = type == null || type.isBlank() ? null : normalizeType(type);
        if (isolation.isPlatformAdministrator(caller)) {
            return repository.searchAll(pspIdFilter, platformWideFilter, normalizedType, active, pageable);
        }
        Long own = ownPsp(caller);
        if (pspIdFilter != null && !pspIdFilter.equals(own)) {
            throw new SecurityException("Cannot access data from another PSP");
        }
        return repository.searchForTenant(own, normalizedType, active, pageable);
    }

    /** One entry, if visible to the caller (another PSP's entry is reported as not found). */
    @Transactional(readOnly = true)
    public PaymentBlacklistEntry get(Long id) {
        User caller = requireCaller();
        PaymentBlacklistEntry entry = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blacklist entry not found: " + id));
        if (!isolation.isPlatformAdministrator(caller) && !Boolean.TRUE.equals(entry.getPlatformWide())
                && !ownPsp(caller).equals(entry.getPspId())) {
            throw new IllegalArgumentException("Blacklist entry not found: " + id);
        }
        return entry;
    }

    /**
     * Adds an entry (or re-activates a removed one).
     *
     * @throws IllegalStateException when an active entry for the same scope/type/value exists (409)
     */
    @Transactional
    public PaymentBlacklistEntry create(CreateRequest request, String ipAddress) {
        User caller = requireCaller();
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String type = normalizeType(request.entryType());
        String value = normalizeValue(type, request.value());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        boolean platformAdmin = isolation.isPlatformAdministrator(caller);
        boolean platformWide = Boolean.TRUE.equals(request.platformWide());
        Long pspId;
        if (platformAdmin) {
            if (platformWide && request.pspId() != null) {
                throw new IllegalArgumentException("a platform-wide entry cannot also name a pspId");
            }
            if (!platformWide && request.pspId() == null) {
                throw new IllegalArgumentException("pspId is required unless platformWide=true");
            }
            pspId = platformWide ? null : request.pspId();
        } else {
            if (platformWide) {
                throw new SecurityException("Only platform administrators can manage the platform-wide blacklist");
            }
            Long own = ownPsp(caller);
            if (request.pspId() != null && !request.pspId().equals(own)) {
                throw new SecurityException("Cannot access data from another PSP");
            }
            pspId = own;
        }

        Optional<PaymentBlacklistEntry> existing = pspId == null
                ? repository.findPlatformAny(type, value)
                : repository.findTenantAny(pspId, type, value);
        PaymentBlacklistEntry entry;
        Map<String, Object> before = null;
        if (existing.isPresent()) {
            entry = existing.get();
            if (Boolean.TRUE.equals(entry.getActive())) {
                throw new IllegalStateException("Blacklist entry already exists: " + entry.getId());
            }
            before = snapshot(entry);
            entry.setActive(true);
        } else {
            entry = new PaymentBlacklistEntry();
            entry.setPspId(pspId);
            entry.setPlatformWide(pspId == null);
            entry.setEntryType(type);
            entry.setEntryValue(value);
        }
        entry.setReason(request.reason());
        entry.setExpiresAt(request.expiresAt());
        entry.setCreatedBy(caller.getId());
        PaymentBlacklistEntry saved = repository.save(entry);
        syncCache(saved);
        audit.logAction(caller, before == null ? "BLACKLIST_ADD" : "BLACKLIST_REACTIVATE", "PAYMENT_BLACKLIST",
                String.valueOf(saved.getId()), before, snapshot(saved), ipAddress, request.reason());
        log.info("Blacklist entry {} {} for {} by {}", saved.getId(), type,
                pspId == null ? "platform" : "psp " + pspId, caller.getUsername());
        return saved;
    }

    /** Changes reason / expiry / active flag of an entry the caller may manage. */
    @Transactional
    public PaymentBlacklistEntry update(Long id, UpdateRequest request, String ipAddress) {
        User caller = requireCaller();
        PaymentBlacklistEntry entry = requireManageable(id, caller);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        Map<String, Object> before = snapshot(entry);
        if (request.reason() != null) {
            entry.setReason(request.reason());
        }
        if (Boolean.TRUE.equals(request.clearExpiry())) {
            entry.setExpiresAt(null);
        } else if (request.expiresAt() != null) {
            if (!request.expiresAt().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("expiresAt must be in the future");
            }
            entry.setExpiresAt(request.expiresAt());
        }
        if (request.active() != null) {
            entry.setActive(request.active());
        }
        PaymentBlacklistEntry saved = repository.save(entry);
        syncCache(saved);
        audit.logAction(caller, "BLACKLIST_UPDATE", "PAYMENT_BLACKLIST", String.valueOf(id),
                before, snapshot(saved), ipAddress, request.reason());
        return saved;
    }

    /** Soft-deletes an entry (active=false) and removes it from the cache. */
    @Transactional
    public void remove(Long id, String reason, String ipAddress) {
        User caller = requireCaller();
        PaymentBlacklistEntry entry = requireManageable(id, caller);
        Map<String, Object> before = snapshot(entry);
        entry.setActive(false);
        PaymentBlacklistEntry saved = repository.save(entry);
        syncCache(saved);
        audit.logAction(caller, "BLACKLIST_REMOVE", "PAYMENT_BLACKLIST", String.valueOf(id),
                before, snapshot(saved), ipAddress, reason);
    }

    /** Entry the caller may change: platform admins any; PSP users only their PSP's (others -> 404/403). */
    private PaymentBlacklistEntry requireManageable(Long id, User caller) {
        PaymentBlacklistEntry entry = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blacklist entry not found: " + id));
        if (isolation.isPlatformAdministrator(caller)) {
            return entry;
        }
        if (Boolean.TRUE.equals(entry.getPlatformWide()) && entry.getPspId() == null) {
            throw new SecurityException("Only platform administrators can manage the platform-wide blacklist");
        }
        if (!ownPsp(caller).equals(entry.getPspId())) {
            throw new IllegalArgumentException("Blacklist entry not found: " + id);
        }
        return entry;
    }

    /** Mirrors the row into the Redis set used by the hot path (only permanent active entries are cached). */
    private void syncCache(PaymentBlacklistEntry e) {
        if (Boolean.TRUE.equals(e.getActive()) && e.getExpiresAt() == null) {
            cache.addToBlacklist(e.getPspId(), e.getEntryType(), e.getEntryValue());
        } else {
            cache.removeFromBlacklist(e.getPspId(), e.getEntryType(), e.getEntryValue());
        }
    }

    /** Audit view of an entry (value is already a hash for PAN entries). */
    private static Map<String, Object> snapshot(PaymentBlacklistEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("pspId", e.getPspId());
        m.put("platformWide", e.getPlatformWide());
        m.put("entryType", e.getEntryType());
        m.put("entryValue", e.getEntryValue());
        m.put("reason", e.getReason());
        m.put("active", e.getActive());
        m.put("expiresAt", e.getExpiresAt() == null ? null : e.getExpiresAt().toString());
        return m;
    }

    /** Validates and lower-cases the entry type. */
    static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("entryType is required");
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new IllegalArgumentException("entryType must be one of " + TYPES);
        }
        return t;
    }

    /** Validates the value; a raw PAN is converted to its lookup hash and never stored. */
    String normalizeValue(String type, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
        String v = value.trim();
        if ("pan".equals(type)) {
            String digits = v.replaceAll("[\\s-]", "");
            if (RAW_PAN.matcher(digits).matches()) {
                return hasher.hashPan(digits);
            }
            if (HEX_64.matcher(v).matches()) {
                return v.toLowerCase(Locale.ROOT);
            }
            throw new IllegalArgumentException("pan value must be a 12-19 digit PAN or its 64-hex lookup hash");
        }
        if (v.length() > 255) {
            throw new IllegalArgumentException("value is too long (max 255)");
        }
        return v;
    }

    /** Authenticated caller or 403. */
    private User requireCaller() {
        User caller = isolation.getCurrentUser();
        if (caller == null) {
            throw new SecurityException("User not authenticated");
        }
        return caller;
    }

    /** The caller's PSP id; a non-platform user without one is a misconfiguration (403). */
    private static Long ownPsp(User caller) {
        if (caller.getPsp() == null || caller.getPsp().getPspId() == null) {
            throw new SecurityException("User has no PSP assigned");
        }
        return caller.getPsp().getPspId();
    }
}
