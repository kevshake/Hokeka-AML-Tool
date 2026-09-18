package com.posgateway.aml.service.psp;

import com.posgateway.aml.dto.psp.PspRegistrationRequest;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PricingTierRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.SubscriptionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.rules.RuleProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the fix for W27-8: PspsListPage's register form has always sent billingCycle, but
 * PspRegistrationRequest had no such field -- Spring silently ignored the unknown JSON property,
 * so every PSP registered through the UI got a hardcoded default regardless of what the operator
 * actually selected.
 */
@ExtendWith(MockitoExtension.class)
class PspServiceRegistrationTest {

    @Mock private PspRepository pspRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RuleProvisioningService ruleProvisioningService;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PricingTierRepository pricingTierRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private WebhookOutboxService webhookOutboxService;

    @InjectMocks private PspService service;

    @Test
    void registerPspPersistsTheRequestedBillingCycleInsteadOfAlwaysTheDefault() {
        ReflectionTestUtils.setField(service, "defaultTierCode", "STARTER");
        ReflectionTestUtils.setField(service, "trialDays", 14);

        when(pspRepository.findByPspCode("ACME")).thenReturn(Optional.empty());
        when(pspRepository.save(any(Psp.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pricingTierRepository.findByTierCode("STARTER")).thenReturn(Optional.empty());

        PspRegistrationRequest request = PspRegistrationRequest.builder()
                .pspCode("ACME")
                .legalName("Acme PSP Ltd")
                .billingCycle("YEARLY")
                .build();

        service.registerPsp(request);

        ArgumentCaptor<Psp> captor = ArgumentCaptor.forClass(Psp.class);
        verify(pspRepository).save(captor.capture());
        assertEquals("YEARLY", captor.getValue().getBillingCycle());
    }

    @Test
    void registerPspFallsBackToMonthlyWhenBillingCycleOmitted() {
        ReflectionTestUtils.setField(service, "defaultTierCode", "STARTER");
        ReflectionTestUtils.setField(service, "trialDays", 14);

        when(pspRepository.findByPspCode("BETA")).thenReturn(Optional.empty());
        when(pspRepository.save(any(Psp.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pricingTierRepository.findByTierCode("STARTER")).thenReturn(Optional.empty());

        PspRegistrationRequest request = PspRegistrationRequest.builder()
                .pspCode("BETA")
                .legalName("Beta PSP Ltd")
                .build();

        service.registerPsp(request);

        ArgumentCaptor<Psp> captor = ArgumentCaptor.forClass(Psp.class);
        verify(pspRepository).save(captor.capture());
        assertEquals("MONTHLY", captor.getValue().getBillingCycle());
    }
}
