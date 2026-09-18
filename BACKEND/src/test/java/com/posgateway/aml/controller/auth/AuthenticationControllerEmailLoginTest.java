package com.posgateway.aml.controller.auth;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the fix for W20-12: logging in with an email address (as opposed to the DB username)
 * always failed with "User not found after authentication", even though authentication itself
 * succeeded, because the post-auth lookup re-queried findByUsername() with the raw request-body
 * value instead of the resolved authentication.getName().
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationControllerEmailLoginTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthenticationController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginByEmailSucceedsBecauseTheResolvedUsernameIsUsedForTheLookup() {
        // The user types their email as the "username" field.
        String emailTyped = "jdoe@example.com";
        String password = "correct-horse-battery-staple";

        // The real DB user has a different, canonical username.
        User dbUser = new User();
        dbUser.setId(42L);
        dbUser.setUsername("jdoe");
        dbUser.setEmail(emailTyped);
        dbUser.setEnabled(true);

        // authenticationManager.authenticate() delegates to CustomUserDetailsService internally
        // (mocked away here) and returns an authenticated token whose principal/name is the
        // RESOLVED DB username "jdoe" -- not the raw email the user typed. That's the crux of
        // the bug: the controller must use this resolved name, not the raw request field.
        Authentication resolvedAuth = new UsernamePasswordAuthenticationToken(
                "jdoe", password, java.util.Collections.emptyList());
        when(authenticationManager.authenticate(any())).thenReturn(resolvedAuth);

        // findByUsername("jdoe") (the resolved name) succeeds.
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(dbUser));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(session);
        when(session.getId()).thenReturn("session-123");

        ResponseEntity<Map<String, Object>> response = controller.login(
                Map.of("username", emailTyped, "password", password), request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));

        // Confirms the fix directly: the raw typed email must never be what's looked up.
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).findByUsername(emailTyped);
    }
}
