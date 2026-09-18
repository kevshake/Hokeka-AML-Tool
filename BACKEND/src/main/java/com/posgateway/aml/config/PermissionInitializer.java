package com.posgateway.aml.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@SuppressWarnings("null")
public class PermissionInitializer implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionInitializer.class);
    private static final String DEFAULT_PASSWORD = "Hokeka2026!";

    private final com.posgateway.aml.service.RoleService roleService;
    private final com.posgateway.aml.repository.UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.posgateway.aml.repository.RoleRepository roleRepository;

    public PermissionInitializer(com.posgateway.aml.service.RoleService roleService,
            com.posgateway.aml.repository.UserRepository userRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            com.posgateway.aml.repository.RoleRepository roleRepository) {
        this.roleService = roleService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing default role permissions...");
        try {
            roleService.initDefaultRoles();
            log.info("Default role permissions initialized successfully");
            seedDemoUsers();
        } catch (Exception e) {
            log.error("Failed to initialize permissions/users: {}", e.getMessage(), e);
        }
    }

    private void seedDemoUsers() {
        seedUser("super.admin",  "super.admin@hokeka.com",  "Super",       "Admin",        "SUPER_ADMIN");
        seedUser("admin",        "admin@hokeka.com",        "Platform",    "Admin",        "ADMIN");
        seedUser("compliance",   "compliance@hokeka.com",   "Compliance",  "Officer",      "COMPLIANCE_OFFICER");
        seedUser("investigator", "investigator@hokeka.com", "Case",        "Investigator", "INVESTIGATOR");
        seedUser("analyst",      "analyst@hokeka.com",      "Risk",        "Analyst",      "ANALYST");
        seedUser("viewer",       "viewer@hokeka.com",       "View",        "Only",         "VIEWER");
    }

    private void seedUser(String username, String email,
                          String firstName, String lastName, String roleName) {
        java.util.Optional<com.posgateway.aml.entity.Role> roleOpt =
                roleRepository.findByNameAndPspIsNull(roleName);
        if (roleOpt.isEmpty()) {
            log.warn("Role {} not found, cannot seed user {}", roleName, username);
            return;
        }
        com.posgateway.aml.entity.Role role = roleOpt.get();
        java.util.Optional<com.posgateway.aml.entity.User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            com.posgateway.aml.entity.User u = existing.get();
            u.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            u.setRole(role);
            userRepository.save(u);
            log.info("User '{}' password synced", username);
        } else {
            com.posgateway.aml.entity.User u = com.posgateway.aml.entity.User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(role)
                    .psp(null)
                    .enabled(true)
                    .build();
            userRepository.save(u);
            log.info("User '{}' created with role {}", username, roleName);
        }
    }
}
