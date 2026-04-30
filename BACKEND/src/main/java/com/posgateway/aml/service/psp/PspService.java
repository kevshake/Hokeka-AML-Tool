package com.posgateway.aml.service.psp;

import com.posgateway.aml.dto.psp.PspRegistrationRequest;
import com.posgateway.aml.dto.psp.PspUserCreationRequest;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// @RequiredArgsConstructor removed
@Service
public class PspService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PspService.class);

    private final PspRepository pspRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    @SuppressWarnings("unused")
    private final PasswordEncoder passwordEncoder;

    public PspService(PspRepository pspRepository, UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.pspRepository = pspRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }
    // User can uncomment or
    // inject if available

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
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .paymentTerms(request.getPaymentTerms() != null ? request.getPaymentTerms() : 30)
                .brandingTheme(request.getBrandingTheme() != null ? request.getBrandingTheme() : "default")
                .logoUrl(request.getLogoUrl())
                .status("PENDING")
                .build();

        return pspRepository.save(psp);
    }

    @Transactional
    public Psp updatePspProfile(Long pspId, com.posgateway.aml.dto.psp.PspUpdateRequest request) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> new IllegalArgumentException("PSP not found"));

        log.info("Updating PSP profile for ID: {}", pspId);

        if (request.getTradingName() != null) psp.setTradingName(request.getTradingName());
        if (request.getContactEmail() != null) psp.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) psp.setContactPhone(request.getContactPhone());
        if (request.getContactAddress() != null) psp.setContactAddress(request.getContactAddress());
        if (request.getBrandingTheme() != null) psp.setBrandingTheme(request.getBrandingTheme());
        if (request.getLogoUrl() != null) psp.setLogoUrl(request.getLogoUrl());

        return pspRepository.save(psp);
    }

    @Transactional
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

        String encodedPassword = request.getPassword(); // REPLACE WITH ENC when PasswordEncoder available

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
            // Verify password - replace with encoder check
            if (user.getPasswordHash().equals(rawPassword)) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }
}
