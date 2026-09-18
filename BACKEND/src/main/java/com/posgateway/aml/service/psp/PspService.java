package com.posgateway.aml.service.psp;

import com.posgateway.aml.dto.psp.PspRegistrationRequest;
import com.posgateway.aml.dto.psp.PspUserCreationRequest;
import com.posgateway.aml.entity.billing.PricingTier;
import com.posgateway.aml.entity.billing.Subscription;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PricingTierRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.SubscriptionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// @RequiredArgsConstructor removed
@Service
public class PspService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PspService.class);

    private final PspRepository pspRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.posgateway.aml.service.rules.RuleProvisioningService ruleProvisioningService;
    private final SubscriptionRepository subscriptionRepository;
    private final PricingTierRepository pricingTierRepository;
    private final InvoiceRepository invoiceRepository;

    /** Tier code every new PSP is placed on if it exists; otherwise the cheapest active tier is used. */
    @Value("${saas.onboarding.default-tier-code:STARTER}")
    private String defaultTierCode;

    /** Length of the initial trial in days (0 = start ACTIVE with no trial). */
    @Value("${saas.onboarding.trial-days:14}")
    private int trialDays;

    public PspService(PspRepository pspRepository, UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            com.posgateway.aml.service.rules.RuleProvisioningService ruleProvisioningService,
            SubscriptionRepository subscriptionRepository,
            PricingTierRepository pricingTierRepository,
            InvoiceRepository invoiceRepository) {
        this.pspRepository = pspRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.ruleProvisioningService = ruleProvisioningService;
        this.subscriptionRepository = subscriptionRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Reactivate a PSP that was suspended for non-payment once it has cleared its dues (no OVERDUE
     * and no past-due SENT invoices remain). No-op for a PSP that is not SUSPENDED or still owes.
     * Called on payment so paying an invoice lifts the dunning suspension automatically.
     */
    @Transactional
    @CacheEvict(cacheNames = "psps", key = "#pspId")
    public boolean reactivateIfDuesCleared(Long pspId) {
        if (pspId == null) {
            return false;
        }
        Psp psp = pspRepository.findById(pspId).orElse(null);
        if (psp == null || !"SUSPENDED".equals(psp.getStatus())) {
            return false;
        }
        long outstanding = invoiceRepository.countOutstanding(pspId, LocalDate.now());
        if (outstanding > 0) {
            log.debug("PSP {} still has {} outstanding invoice(s) — staying suspended", pspId, outstanding);
            return false;
        }
        // Activate inline (not via updatePspStatus) so this method's own @CacheEvict fires — a
        // self-invocation would bypass the proxy and leave the psps cache stale.
        psp.activate();
        pspRepository.save(psp);
        log.info("Reactivated PSP {} — all outstanding invoices cleared", pspId);
        return true;
    }
    // User can uncomment or
    // inject if available

    /**
     * Cacheable PSP lookup by primary key.  All hot-path code that only needs to
     * read PSP configuration should call this method rather than hitting the
     * repository directly, so the result is served from the "psps" Caffeine cache
     * (15-min TTL, 500-entry max) on subsequent calls.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "psps", key = "#pspId")
    public Psp getPsp(Long pspId) {
        return pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found: " + pspId));
    }

    @Transactional
    public Psp registerPsp(PspRegistrationRequest request) {
        log.info("Registering new PSP: {}", request.getPspCode());

        if (pspRepository.findByPspCode(request.getPspCode()).isPresent()) {
            throw new IllegalArgumentException("PSP Code already exists");
        }

        Psp psp = Psp.builder()
                .pspCode(request.getPspCode())
                .legalName(request.getLegalName())
                .tradingName(request.getTradingName())
                .country(request.getCountry())
                .registrationNumber(request.getRegistrationNumber())
                .taxId(request.getTaxId())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .contactAddress(request.getContactAddress())
                .billingPlan(request.getBillingPlan() != null ? request.getBillingPlan() : "PAY_AS_YOU_GO")
                .billingCycle(request.getBillingCycle() != null ? request.getBillingCycle() : "MONTHLY")
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .paymentTerms(request.getPaymentTerms() != null ? request.getPaymentTerms() : 30)
                .brandingTheme(request.getBrandingTheme() != null ? request.getBrandingTheme() : "default")
                .logoUrl(request.getLogoUrl())
                .status("PENDING")
                .build();

        Psp saved = pspRepository.save(psp);

        // Seed the PSP's profile with editable copies of the default rule catalog. Best-effort:
        // a hiccup here must not fail onboarding — the idempotent backfill (and re-provisioning)
        // will fill any gap.
        try {
            ruleProvisioningService.copyDefaultRulesToPsp(saved.getPspId());
        } catch (Exception e) {
            log.warn("Default-rule provisioning for PSP {} deferred: {}", saved.getPspId(), e.getMessage());
        }

        // Place the PSP on a real subscription so it is visible to the billing cycle and has plan
        // entitlements from day one. Previously a registered PSP had no subscription and was invisible
        // to billing forever. Best-effort: onboarding must not fail if the tier catalogue is missing.
        try {
            provisionDefaultSubscription(saved);
        } catch (Exception e) {
            log.warn("Default-subscription provisioning for PSP {} deferred: {}", saved.getPspId(), e.getMessage());
        }

        return saved;
    }

    /**
     * Ensure the PSP has exactly one active subscription on the default tier. Idempotent: a PSP that
     * already has an ACTIVE/TRIAL subscription is left untouched. Resolves the tier by the configured
     * default code, falling back to the cheapest active tier so provisioning still succeeds if the
     * named tier was never seeded.
     */
    @Transactional
    public Optional<Subscription> provisionDefaultSubscription(Psp psp) {
        if (subscriptionRepository.findActiveByPspId(psp.getPspId()).isPresent()) {
            return subscriptionRepository.findActiveByPspId(psp.getPspId());
        }
        PricingTier tier = resolveDefaultTier();
        if (tier == null) {
            log.warn("No pricing tier available — cannot provision a subscription for PSP {}", psp.getPspId());
            return Optional.empty();
        }

        LocalDate today = LocalDate.now();
        Subscription sub = new Subscription();
        sub.setPsp(psp);
        sub.setPricingTier(tier);
        sub.setBillingCurrency(psp.getCurrency() != null ? psp.getCurrency() : "USD");
        sub.setBillingCycle("MONTHLY");
        sub.setContractStart(today);
        sub.setCreatedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        if (trialDays > 0) {
            sub.setStatus("TRIAL");
            sub.setTrialEndsAt(today.plusDays(trialDays));
        } else {
            sub.setStatus("ACTIVE");
        }
        Subscription saved = subscriptionRepository.save(sub);
        log.info("Provisioned {} subscription (tier {}) for PSP {}", saved.getStatus(), tier.getTierCode(),
                psp.getPspId());
        return Optional.of(saved);
    }

    private PricingTier resolveDefaultTier() {
        Optional<PricingTier> named = pricingTierRepository.findByTierCode(defaultTierCode);
        if (named.isPresent()) {
            return named.get();
        }
        List<PricingTier> active = pricingTierRepository.findAllActive(); // cheapest first
        return active.isEmpty() ? null : active.get(0);
    }

    @Transactional
    @CacheEvict(cacheNames = "psps", key = "#pspId")
    public Psp updatePspProfile(Long pspId, com.posgateway.aml.dto.psp.PspUpdateRequest request) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        log.info("Updating PSP profile for ID: {}", pspId);

        if (request.getLegalName() != null) psp.setLegalName(request.getLegalName());
        if (request.getTradingName() != null) psp.setTradingName(request.getTradingName());
        if (request.getCountry() != null) psp.setCountry(request.getCountry());
        if (request.getRegistrationNumber() != null) psp.setRegistrationNumber(request.getRegistrationNumber());
        if (request.getTaxId() != null) psp.setTaxId(request.getTaxId());
        if (request.getContactEmail() != null) psp.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) psp.setContactPhone(request.getContactPhone());
        if (request.getContactAddress() != null) psp.setContactAddress(request.getContactAddress());
        if (request.getBillingPlan() != null) psp.setBillingPlan(request.getBillingPlan());
        if (request.getBillingCycle() != null) psp.setBillingCycle(request.getBillingCycle());
        if (request.getCurrency() != null) psp.setCurrency(request.getCurrency());
        if (request.getPaymentTerms() != null) psp.setPaymentTerms(request.getPaymentTerms());
        if (request.getIsTestMode() != null) psp.setIsTestMode(request.getIsTestMode());
        if (request.getBrandingTheme() != null) psp.setBrandingTheme(request.getBrandingTheme());
        if (request.getLogoUrl() != null) psp.setLogoUrl(request.getLogoUrl());
        if (request.getPrimaryColor() != null) psp.setPrimaryColor(request.getPrimaryColor());
        if (request.getSecondaryColor() != null) psp.setSecondaryColor(request.getSecondaryColor());
        if (request.getAccentColor() != null) psp.setAccentColor(request.getAccentColor());

        return pspRepository.save(psp);
    }

    @Transactional
    @CacheEvict(cacheNames = "psps", key = "#pspId")
    public Psp updatePspTheme(Long pspId, com.posgateway.aml.dto.psp.PspThemeUpdateRequest request) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        log.info("Updating PSP theme for ID: {}", pspId);

        if (request.getBrandingTheme() != null) psp.setBrandingTheme(request.getBrandingTheme());
        if (request.getPrimaryColor() != null) psp.setPrimaryColor(request.getPrimaryColor());
        if (request.getSecondaryColor() != null) psp.setSecondaryColor(request.getSecondaryColor());
        if (request.getAccentColor() != null) psp.setAccentColor(request.getAccentColor());
        if (request.getLogoUrl() != null) psp.setLogoUrl(request.getLogoUrl());
        if (request.getFontFamily() != null) psp.setFontFamily(request.getFontFamily());
        if (request.getFontSize() != null) psp.setFontSize(request.getFontSize());
        if (request.getButtonRadius() != null) psp.setButtonRadius(request.getButtonRadius());
        if (request.getButtonStyle() != null) psp.setButtonStyle(request.getButtonStyle());
        if (request.getNavStyle() != null) psp.setNavStyle(request.getNavStyle());

        return pspRepository.save(psp);
    }

    @Transactional
    @CacheEvict(cacheNames = "psps", key = "#pspId")
    public void updatePspStatus(Long pspId, String status) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        log.info("Updating PSP {} status to {}", pspId, status);

        switch (status) {
            case "ACTIVE":
                psp.activate();
                break;
            case "SUSPENDED":
                psp.suspend("Manual suspension");
                break;
            case "TERMINATED":
                psp.terminate();
                break;
            default:
                psp.setStatus(status);
        }

        pspRepository.save(psp);
    }

    @Transactional
    public com.posgateway.aml.entity.User createPspUser(PspUserCreationRequest request) {
        Psp psp = pspRepository.findById(request.getPspId())
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User email already exists");
        }

        // Find Role
        String roleName = request.getRole() != null ? request.getRole() : "OPERATOR";
        com.posgateway.aml.entity.Role role = roleRepository.findByNameAndPsp(roleName, psp)
                .orElseGet(() -> roleRepository.findByNameAndPspIsNull(roleName)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName)));

        // Validate Permissions (Optional: if Role is dynamic, permissions come from
        // Role,
        // but if User has specific overrides we might need logic. For now, Role governs
        // permissions)

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        com.posgateway.aml.entity.User user = com.posgateway.aml.entity.User.builder()
                .psp(psp)
                .username(request.getEmail()) // Using email as username for PSP users
                .email(request.getEmail())
                .firstName(request.getFullName().split(" ")[0])
                .lastName(request.getFullName().contains(" ")
                        ? request.getFullName().substring(request.getFullName().indexOf(" ") + 1)
                        : "")
                .passwordHash(encodedPassword)
                .role(role)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<com.posgateway.aml.entity.User> authenticatePspUser(String email, String rawPassword) {
        Optional<com.posgateway.aml.entity.User> userOpt = userRepository.findByUsername(email); // Username is email

        if (userOpt.isPresent()) {
            com.posgateway.aml.entity.User user = userOpt.get();
            if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }
}
