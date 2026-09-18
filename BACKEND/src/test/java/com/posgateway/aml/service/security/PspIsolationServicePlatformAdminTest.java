package com.posgateway.aml.service.security;

import com.posgateway.aml.config.tenant.PspTenantFilter;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PspIsolationServicePlatformAdminTest {

    @Mock
    private UserRepository userRepository;

    private PspIsolationService service;

    @BeforeEach
    void setUp() {
        service = new PspIsolationService(userRepository);
    }

    @Test
    void platformAdminWithHokekaPlatformPspIsCrossTenant() {
        Psp hokeka = new Psp();
        hokeka.setPspId(5L);
        hokeka.setPspCode("HOKEKA_PLATFORM");

        Role role = new Role();
        role.setName("PLATFORM_ADMIN");

        User user = new User();
        user.setUsername("platform.admin");
        user.setRole(role);
        user.setPsp(hokeka);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a", java.util.List.of()));

        assertTrue(service.isPlatformAdministrator(user));
        assertEquals(PspTenantFilter.PLATFORM_ADMIN_PSP_ID, service.getCurrentUserPspId());
    }
}
