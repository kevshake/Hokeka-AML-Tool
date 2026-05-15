package com.posgateway.aml.config;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final List<String> PROD_PROFILES = Arrays.asList("prod", "production");

    /** Character set used to build the random fallback admin password. */
    private static final String RANDOM_PASSWORD_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final int RANDOM_PASSWORD_LENGTH = 32;

    private final Environment environment;

    @Value("${seed.admin.password:}")
    private String seedAdminPassword;

    public DataSeeder(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Ensure ADMIN Role exists
            Optional<Role> adminRoleOpt = roleRepository.findByNameAndPspIsNull("ADMIN");
            Role adminRole;
            if (adminRoleOpt.isEmpty()) {
                adminRole = new Role();
                adminRole.setName("ADMIN");
                adminRole.setDescription("System Administrator");
                adminRole = roleRepository.save(adminRole);
                log.info("DataSeeder: Created ADMIN role");
            } else {
                adminRole = adminRoleOpt.get();
            }

            // 2. Ensure Admin User exists
            if (userRepository.findByUsername("admin").isEmpty()) {
                if (userRepository.findByEmail("admin@posgateway.com").isPresent()) {
                    log.info("DataSeeder: User with email admin@posgateway.com already exists. Skipping creation.");
                    return;
                }

                String adminPassword = resolveAdminPassword();

                try {
                    User admin = new User();
                    admin.setUsername("admin");
                    admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                    admin.setEmail("admin@posgateway.com");
                    admin.setRole(adminRole);
                    admin.setFirstName("System");
                    admin.setLastName("Admin");
                    admin.setEnabled(true);
                    userRepository.save(admin);
                    log.info("DataSeeder: Created default admin user (username=admin)");
                } catch (Exception e) {
                    log.error("DataSeeder: Failed to create admin user: {}", e.getMessage());
                    // Do not rethrow, let app startup continue
                }
            }
        };
    }

    /**
     * Resolve the password for the seeded admin user.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code seed.admin.password} (env {@code SEED_ADMIN_PASSWORD}) is set, use it.</li>
     *   <li>If unset and the active profile contains {@code prod}/{@code production}, fail-fast
     *       with {@link IllegalStateException} — refusing to boot prevents a predictable default
     *       admin credential from ever existing in production.</li>
     *   <li>If unset in dev/test, generate a cryptographically random 32-character password
     *       using {@link SecureRandom}. The password value is NEVER logged; only the username
     *       and a warning instructing operators to set {@code SEED_ADMIN_PASSWORD} are emitted.</li>
     * </ol>
     */
    private String resolveAdminPassword() {
        if (seedAdminPassword != null && !seedAdminPassword.isBlank()) {
            return seedAdminPassword;
        }

        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(PROD_PROFILES::contains);
        if (isProd) {
            throw new IllegalStateException(
                    "seed.admin.password (env SEED_ADMIN_PASSWORD) is required when running with a "
                            + "production profile but was blank. Set SEED_ADMIN_PASSWORD env var before "
                            + "starting in production. Refusing to boot with a predictable default admin "
                            + "password.");
        }

        log.warn("DataSeeder: Generated random admin password — set SEED_ADMIN_PASSWORD env var to "
                + "control it (username=admin)");
        return generateRandomPassword(RANDOM_PASSWORD_LENGTH);
    }

    /**
     * Generate a cryptographically-random password of the requested length using
     * {@link SecureRandom}. Only used as a non-prod fallback when the operator did not
     * supply {@code SEED_ADMIN_PASSWORD}.
     */
    private String generateRandomPassword(int length) {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_PASSWORD_ALPHABET.charAt(rng.nextInt(RANDOM_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
