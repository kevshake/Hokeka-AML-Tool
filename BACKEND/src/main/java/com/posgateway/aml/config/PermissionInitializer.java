package com.posgateway.aml.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Boot-time initialiser for the global system roles and their default permission sets.
 *
 * <p>This runner used to also upsert a set of demo users with a hard-coded password on every
 * boot, in every profile — which meant the demo credentials worked in production and silently
 * overwrote any password an operator had set. User seeding now lives in
 * {@link DemoAccountSeeder} (dev/test/local profiles only) and {@link DataSeeder} (the single
 * operator-controlled {@code admin} account); {@link DefaultCredentialGuard} neutralises any
 * demo account that still carries the known default password outside dev/test.
 */
@Component
@Order(10)
public class PermissionInitializer implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionInitializer.class);

    private final com.posgateway.aml.service.RoleService roleService;

    public PermissionInitializer(com.posgateway.aml.service.RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * Creates or refreshes the global system roles (SUPER_ADMIN, ADMIN, PLATFORM_ADMIN, PSP_ADMIN, …)
     * with their default permission sets. A failure is logged but does not abort boot, matching the
     * previous behaviour: roles already present in the database keep working.
     */
    @Override
    public void run(String... args) {
        log.info("Initializing default role permissions...");
        try {
            roleService.initDefaultRoles();
            log.info("Default role permissions initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize default role permissions: {}", e.getMessage(), e);
        }
    }
}
