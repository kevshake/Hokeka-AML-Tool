package com.posgateway.aml.config;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Seeds login-ready demo accounts — <b>only</b> in the {@code dev}, {@code test} and {@code local}
 * profiles.
 *
 * <p>Two groups are handled:
 * <ul>
 *   <li>{@link #GLOBAL_DEMO_USERS} — platform-level accounts created here if missing.</li>
 *   <li>{@link DefaultCredentialGuard#V127_DEMO_USERNAMES} — PSP-scoped accounts inserted by
 *       migration V127 and disabled by V253 on every database. In dev/test they are re-enabled
 *       here so local demos keep working; they are never created here because their per-PSP roles
 *       only exist where V127 ran.</li>
 * </ul>
 *
 * <p>The password comes from {@code hokeka.demo-accounts.password}, which is set only in
 * {@code application-dev.properties} / {@code application-test.properties}; production property
 * files never define it and this bean is not even instantiated outside the demo profiles.
 */
@Component
@Profile({"dev", "test", "local"})
@Order(15)
public class DemoAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    /** Platform-level demo accounts: username, email, first name, last name, global role. */
    static final List<String[]> GLOBAL_DEMO_USERS = List.of(
            new String[]{"super.admin",  "super.admin@hokeka.com",  "Super",      "Admin",        "SUPER_ADMIN"},
            new String[]{"admin",        "admin@hokeka.com",        "Platform",   "Admin",        "ADMIN"},
            new String[]{"compliance",   "compliance@hokeka.com",   "Compliance", "Officer",      "COMPLIANCE_OFFICER"},
            new String[]{"investigator", "investigator@hokeka.com", "Case",       "Investigator", "INVESTIGATOR"},
            new String[]{"analyst",      "analyst@hokeka.com",      "Risk",       "Analyst",      "ANALYST"},
            new String[]{"viewer",       "viewer@hokeka.com",       "View",       "Only",         "VIEWER"});

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoAccountSeeder(UserRepository userRepository, RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${hokeka.demo-accounts.password:}") String demoPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    /**
     * Creates/refreshes the global demo users and re-enables the V127 PSP demo users. Skips with a
     * warning when no demo password is configured, so a dev profile without the property simply
     * has no demo logins instead of a predictable one.
     */
    @Override
    public void run(String... args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("DemoAccountSeeder: hokeka.demo-accounts.password is not set; demo accounts not seeded");
            return;
        }
        try {
            String hash = passwordEncoder.encode(demoPassword);
            for (String[] u : GLOBAL_DEMO_USERS) {
                upsertGlobalUser(u[0], u[1], u[2], u[3], u[4], hash);
            }
            for (String username : DefaultCredentialGuard.V127_DEMO_USERNAMES) {
                reenableExisting(username, hash);
            }
        } catch (Exception e) {
            log.error("DemoAccountSeeder: failed to seed demo accounts: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates the user with the given global role, or resets an existing one's password, role and
     * enabled flag so the documented demo login always works in dev/test.
     */
    private void upsertGlobalUser(String username, String email, String firstName, String lastName,
                                  String roleName, String hash) {
        Optional<Role> roleOpt = roleRepository.findByNameAndPspIsNull(roleName);
        if (roleOpt.isEmpty()) {
            log.warn("DemoAccountSeeder: role {} not found, cannot seed user {}", roleName, username);
            return;
        }
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            User u = existing.get();
            u.setPasswordHash(hash);
            u.setRole(roleOpt.get());
            u.setEnabled(true);
            userRepository.save(u);
            log.info("DemoAccountSeeder: demo user '{}' refreshed", username);
            return;
        }
        if (userRepository.findByEmail(email).isPresent()) {
            log.info("DemoAccountSeeder: email {} already used by another user; '{}' not created", email, username);
            return;
        }
        userRepository.save(User.builder()
                .username(username)
                .passwordHash(hash)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .role(roleOpt.get())
                .psp(null)
                .enabled(true)
                .build());
        log.info("DemoAccountSeeder: demo user '{}' created with role {}", username, roleName);
    }

    /** Re-enables a V127 PSP demo user (disabled by V253) with the dev demo password, if it exists. */
    private void reenableExisting(String username, String hash) {
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setPasswordHash(hash);
            u.setEnabled(true);
            userRepository.save(u);
            log.info("DemoAccountSeeder: PSP demo user '{}' re-enabled", username);
        });
    }
}
