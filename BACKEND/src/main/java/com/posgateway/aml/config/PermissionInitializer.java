package com.posgateway.aml.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Permission Initializer
 * Runs on application startup to initialize default role permissions
 * 
 * Order(10): Runs after database migrations but before main app logic
 */
@Component
@Order(10)
@SuppressWarnings("null") // User builder and repository save operations are safe
public class PermissionInitializer implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionInitializer.class);

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

            // Seed default admin user if missing (since ddl-auto=create might have wiped
            // it)
            seedDefaultAdmin();

        } catch (Exception e) {
            log.error("Failed to initialize permissions/users: {}", e.getMessage());
            // Don't fail startup - permissions can be initialized later
        }
    }

    private void seedDefaultAdmin() {
        // Seed or Update ADMIN
        java.util.Optional<com.posgateway.aml.entity.User> existingAdmin = userRepository.findByUsername("admin");
        if (existingAdmin.isPresent()) {
            com.posgateway.aml.entity.User admin = existingAdmin.get();
            // Ensure role is set before saving
            if (admin.getRole() == null) {
                java.util.Optional<com.posgateway.aml.entity.Role> adminRole = roleRepository
                        .findByNameAndPspIsNull("ADMIN");
                if (adminRole.isPresent()) {
                    admin.setRole(adminRole.get());
                } else {
                    log.warn("ADMIN role not found, skipping admin user update");
                    return;
                }
            }
            admin.setPasswordHash(passwordEncoder.encode("password"));
            userRepository.save(admin);
            log.info("Admin password reset to 'password'");
        } else {
            java.util.Optional<com.posgateway.aml.entity.Role> adminRole = roleRepository
                    .findByNameAndPspIsNull("ADMIN");
            if (adminRole.isPresent()) {
                @SuppressWarnings("null")
                com.posgateway.aml.entity.User admin = com.posgateway.aml.entity.User.builder()
                        .username("admin")
                        .passwordHash(passwordEncoder.encode("password"))
                        .email("admin@sys.com")
                        .firstName("System")
                        .lastName("Admin")
                        .role(adminRole.get())
                        .psp(null)
                        .enabled(true)
                        .build();
                userRepository.save(admin);
                log.info("Default admin user 'admin' created successfully");
            } else {
                log.warn("ADMIN role not found, cannot create default admin user");
            }
        }

        // Seed COMPLIANCE user
        if (userRepository.findByUsername("compliance").isEmpty()) {
            java.util.Optional<com.posgateway.aml.entity.Role> compRole = roleRepository
                    .findByNameAndPspIsNull("COMPLIANCE_OFFICER");
            if (compRole.isPresent()) {
                @SuppressWarnings("null")
                com.posgateway.aml.entity.User comp = com.posgateway.aml.entity.User.builder()
                        .username("compliance")
                        .passwordHash(passwordEncoder.encode("password"))
                        .email("compliance@sys.com")
                        .firstName("Compliance")
                        .lastName("Officer")
                        .role(compRole.get())
                        .psp(null)
                        .enabled(true)
                        .build();
                userRepository.save(comp);
                log.info("Default compliance user 'compliance' created successfully");
            } else {
                log.warn("COMPLIANCE_OFFICER role not found, cannot create default compliance user");
            }
        }
    }
}
