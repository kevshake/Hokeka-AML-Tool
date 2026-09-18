package com.posgateway.aml.controller.limits;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.limits.GlobalLimit;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.limits.LimitsManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LimitsAmlSaveIT {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveAmlLimitsUpsertsBothControls() {
        LimitsManagementService limitsService = mock(LimitsManagementService.class);
        UserRepository userRepository = mock(UserRepository.class);
        LimitsManagementController controller = new LimitsManagementController(limitsService, userRepository);

        User principal = new User();
        principal.setId(7L);
        principal.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(principal));

        org.springframework.security.core.userdetails.User secUser =
                new org.springframework.security.core.userdetails.User("admin", "x", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(secUser, "x", Collections.emptyList()));

        when(limitsService.upsertAmlLimits(new BigDecimal("50000"),
                new BigDecimal("250000"), 7L))
                .thenReturn(List.of(new GlobalLimit(), new GlobalLimit()));

        var response = controller.saveAmlLimits(Map.of(
                "transactionLimit", 50000,
                "dailyLimit", 250000));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(limitsService).upsertAmlLimits(new BigDecimal("50000"),
                new BigDecimal("250000"), 7L);
    }
}
