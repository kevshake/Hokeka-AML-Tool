package com.posgateway.aml.controller;

import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.PermissionService;
import com.posgateway.aml.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = UserController.class, properties = "spring.security.enabled=false")
@Import(UserControllerAuthorizationTest.MethodSecurity.class)
class UserControllerAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurity {}

    @Autowired MockMvc mvc;
    @MockBean UserService userService;
    @MockBean PermissionService permissionService;
    @MockBean PspRepository pspRepository;
    @MockBean UserRepository userRepository;

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminCannotUsePlatformUserCrud() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void platformAdminCanUseUserCrud() throws Exception {
        when(userRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty());
        mvc.perform(get("/users")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_USER")
    void authenticatedUserCanReachMeWithoutSuperAdminFallback() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isNotFound());
    }
}
