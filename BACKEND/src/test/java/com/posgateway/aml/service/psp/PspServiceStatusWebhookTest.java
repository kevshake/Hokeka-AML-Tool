package com.posgateway.aml.service.psp;

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

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PspServiceStatusWebhookTest {

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
    void updatePspStatusEnqueuesMerchantStatusChangeWebhook() {
        Psp psp = Psp.builder().pspId(7L).pspCode("ACME").status("ACTIVE").build();
        when(pspRepository.findById(7L)).thenReturn(Optional.of(psp));
        when(pspRepository.save(any(Psp.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updatePspStatus(7L, "SUSPENDED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webhookOutboxService).enqueueForPsp(
                eq(7L),
                eq("MERCHANT_STATUS_CHANGE"),
                payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.matches("webhook\\.merchant_status:7:SUSPENDED:.*"));
        assertEquals("ACTIVE", payloadCaptor.getValue().get("previousStatus"));
        assertEquals("SUSPENDED", payloadCaptor.getValue().get("status"));
    }
}
