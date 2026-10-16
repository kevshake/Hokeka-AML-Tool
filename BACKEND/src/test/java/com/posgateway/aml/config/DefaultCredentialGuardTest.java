package com.posgateway.aml.config;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the hard-coded demo credential finding: demo users must never be created or
 * reset outside dev/test, and accounts still carrying the published default password are neutralised.
 */
class DefaultCredentialGuardTest {

    private static final String DEFAULT_PW = DefaultCredentialGuard.KNOWN_DEFAULT_PASSWORDS.get(0);

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository userRepository;
    private final Map<String, User> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(users.get(inv.<String>getArgument(0))));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(String username, String rawPassword) {
        User u = User.builder().username(username).email(username + "@x.test")
                .passwordHash(encoder.encode(rawPassword)).enabled(true).build();
        users.put(username, u);
        return u;
    }

    @Test
    void demoUserWithDefaultPasswordIsDisabledAndHashInvalidated() {
        User velocity = user("velocity.admin", DEFAULT_PW);
        User analyst = user("analyst", DEFAULT_PW);

        List<String> changed = new DefaultCredentialGuard(userRepository, encoder, "").neutraliseDefaultCredentials();

        assertThat(changed).containsExactlyInAnyOrder("velocity.admin", "analyst");
        for (User u : List.of(velocity, analyst)) {
            assertThat(u.isEnabled()).isFalse();
            assertThat(u.getPasswordHash()).isEqualTo(DefaultCredentialGuard.DISABLED_HASH);
            assertThat(encoder.matches(DEFAULT_PW, u.getPasswordHash())).isFalse();
        }
    }

    @Test
    void adminWithDefaultPasswordIsRotatedToSeedAdminPassword() {
        User admin = user("admin", DEFAULT_PW);

        List<String> changed = new DefaultCredentialGuard(userRepository, encoder, "Operator-Secret-1")
                .neutraliseDefaultCredentials();

        assertThat(changed).containsExactly("admin");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(encoder.matches("Operator-Secret-1", admin.getPasswordHash())).isTrue();
        assertThat(encoder.matches(DEFAULT_PW, admin.getPasswordHash())).isFalse();
    }

    @Test
    void adminWithDefaultPasswordAndNoSeedSecretIsDisabled() {
        User admin = user("admin", DEFAULT_PW);

        new DefaultCredentialGuard(userRepository, encoder, " ").neutraliseDefaultCredentials();

        assertThat(admin.isEnabled()).isFalse();
        assertThat(admin.getPasswordHash()).isEqualTo(DefaultCredentialGuard.DISABLED_HASH);
    }

    @Test
    void rotatedPasswordsAndUnknownUsersAreNeverTouched() {
        User admin = user("admin", "Chosen-By-Operator-9");
        String before = admin.getPasswordHash();
        user("someone.else", DEFAULT_PW);

        List<String> changed = new DefaultCredentialGuard(userRepository, encoder, "Operator-Secret-1")
                .neutraliseDefaultCredentials();

        assertThat(changed).isEmpty();
        assertThat(admin.getPasswordHash()).isEqualTo(before);
        assertThat(admin.isEnabled()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void guardCoversEveryDemoUsernameAndOnlyRunsOutsideDemoProfiles() {
        assertThat(DefaultCredentialGuard.allDemoUsernames()).contains(
                "admin", "super.admin", "platform.admin", "velocity.admin", "compliance", "analyst", "viewer",
                "investigator", "mwananchi.admin", "apex.admin");
        assertThat(DefaultCredentialGuard.class.getAnnotation(Profile.class).value())
                .containsExactly("!dev & !test & !local");
        assertThat(DemoAccountSeeder.class.getAnnotation(Profile.class).value())
                .containsExactlyInAnyOrder("dev", "test", "local");
    }

    @Test
    void permissionInitializerOnlyInitialisesRolesAndNeverTouchesUsers() throws Exception {
        RoleService roleService = mock(RoleService.class);
        new PermissionInitializer(roleService).run();
        verify(roleService).initDefaultRoles();
        verifyNoInteractions(userRepository);
    }

    @Test
    void demoSeederWithoutConfiguredPasswordSeedsNothing() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        new DemoAccountSeeder(userRepository, roleRepository, encoder, "").run();
        verifyNoInteractions(roleRepository);
        verify(userRepository, never()).save(any());
    }

    @Test
    void demoSeederInDevReenablesV127UserAndCreatesGlobalUsers() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        Role role = new Role();
        role.setName("ADMIN");
        when(roleRepository.findByNameAndPspIsNull(anyString())).thenReturn(Optional.of(role));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        User disabled = user("velocity.admin", "irrelevant");
        disabled.setEnabled(false);
        disabled.setPasswordHash(DefaultCredentialGuard.DISABLED_HASH);

        new DemoAccountSeeder(userRepository, roleRepository, encoder, "Dev-Demo-Pw1").run();

        assertThat(disabled.isEnabled()).isTrue();
        assertThat(encoder.matches("Dev-Demo-Pw1", disabled.getPasswordHash())).isTrue();
        verify(userRepository, org.mockito.Mockito.atLeast(DemoAccountSeeder.GLOBAL_DEMO_USERS.size() + 1))
                .save(any(User.class));
    }
}
