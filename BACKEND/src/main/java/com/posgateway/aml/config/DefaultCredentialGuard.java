package com.posgateway.aml.config;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Outside the demo profiles ({@code dev}, {@code test}, {@code local}), neutralises every known demo
 * account that still accepts the historical default password.
 *
 * <p>Older builds upserted demo users with a hard-coded password on every boot and migration V127
 * inserts PSP demo users with the same password. Migration V253 disables the V127 rows whose hash is
 * still the shipped literal, but rows written by the old boot-time seeder carry salted hashes that SQL
 * cannot recognise — so this guard re-checks them with the real {@link PasswordEncoder} on each boot:
 * <ul>
 *   <li>{@code admin}: if {@code SEED_ADMIN_PASSWORD} is configured, the password is rotated to it
 *       (the account stays usable by the operator); otherwise it is disabled.</li>
 *   <li>every other demo username: disabled and its hash replaced by a non-BCrypt sentinel, so no
 *       password can ever match until an administrator re-enables it and resets the password.</li>
 * </ul>
 * Accounts whose password was already changed are never touched.
 */
@Component
@Profile("!dev & !test & !local")
@Order(30)
public class DefaultCredentialGuard implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultCredentialGuard.class);

    /**
     * Passwords that shipped in source/migrations and must be treated as public. Used only as a
     * deny-list to detect accounts that still carry them.
     */
    static final List<String> KNOWN_DEFAULT_PASSWORDS = List.of("Hokeka2026!");

    /** PSP-scoped demo users inserted by migration V127 (and disabled by V253). */
    static final List<String> V127_DEMO_USERNAMES = List.of(
            "super.admin", "platform.admin",
            "velocity.admin", "velocity.compliance", "velocity.user",
            "mwananchi.admin", "mwananchi.compliance", "apex.admin");

    /** Non-BCrypt hash value: {@code BCryptPasswordEncoder.matches} always returns false for it. */
    static final String DISABLED_HASH = "!DISABLED-DEFAULT-CREDENTIAL";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedAdminPassword;

    public DefaultCredentialGuard(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  @Value("${seed.admin.password:}") String seedAdminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedAdminPassword = seedAdminPassword;
    }

    /** All usernames the old seeders could have created with a default password. */
    static Set<String> allDemoUsernames() {
        Set<String> names = new LinkedHashSet<>();
        DemoAccountSeeder.GLOBAL_DEMO_USERS.forEach(u -> names.add(u[0]));
        names.addAll(V127_DEMO_USERNAMES);
        return names;
    }

    /** Runs the check on boot; failures are logged loudly but never block startup. */
    @Override
    public void run(String... args) {
        try {
            List<String> neutralised = neutraliseDefaultCredentials();
            if (!neutralised.isEmpty()) {
                log.warn("DefaultCredentialGuard: neutralised default-password accounts: {}", neutralised);
            }
        } catch (Exception e) {
            log.error("DefaultCredentialGuard: could not verify default credentials: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks each demo username and neutralises the ones that still accept a known default password.
     *
     * @return the usernames that were rotated or disabled
     */
    List<String> neutraliseDefaultCredentials() {
        List<String> changed = new ArrayList<>();
        for (String username : allDemoUsernames()) {
            userRepository.findByUsername(username).ifPresent(user -> {
                if (!acceptsDefaultPassword(user)) {
                    return;
                }
                if ("admin".equals(username) && seedAdminPassword != null && !seedAdminPassword.isBlank()) {
                    user.setPasswordHash(passwordEncoder.encode(seedAdminPassword));
                    log.warn("DefaultCredentialGuard: 'admin' still had the shipped default password; "
                            + "rotated to SEED_ADMIN_PASSWORD");
                } else {
                    user.setPasswordHash(DISABLED_HASH);
                    user.setEnabled(false);
                    log.warn("DefaultCredentialGuard: '{}' still had the shipped default password; account "
                            + "disabled (an administrator must re-enable it and reset the password)", username);
                }
                userRepository.save(user);
                changed.add(username);
            });
        }
        return changed;
    }

    /** True when the stored hash verifies against any known default password. */
    private boolean acceptsDefaultPassword(User user) {
        String hash = user.getPasswordHash();
        if (hash == null || hash.isBlank() || DISABLED_HASH.equals(hash)) {
            return false;
        }
        for (String candidate : KNOWN_DEFAULT_PASSWORDS) {
            try {
                if (passwordEncoder.matches(candidate, hash)) {
                    return true;
                }
            } catch (IllegalArgumentException malformedHash) {
                // A hash in an unknown format cannot be the BCrypt default either.
                return false;
            }
        }
        return false;
    }
}
