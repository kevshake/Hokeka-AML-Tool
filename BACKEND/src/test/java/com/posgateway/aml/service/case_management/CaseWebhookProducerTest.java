package com.posgateway.aml.service.case_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.psp.WebhookOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CaseWebhookProducerTest {

    @Test
    void caseCreationEnqueuesCaseUpdateWebhook() {
        WebhookOutboxService webhookOutboxService = mock(WebhookOutboxService.class);
        CaseCreationService service = new CaseCreationService(
                mock(ComplianceCaseRepository.class),
                new ObjectMapper(),
                null,
                webhookOutboxService,
                mock(CaseEnrichmentService.class),
                mock(RuleDefinitionRepository.class));

        ComplianceCase cCase = new ComplianceCase();
        cCase.setId(99L);
        cCase.setCaseReference("CASE-20260918-ABC");
        cCase.setStatus(CaseStatus.IN_PROGRESS);
        cCase.setPriority(CasePriority.HIGH);
        cCase.setMerchantId(4L);
        cCase.setPspId(2L);
        cCase.setUpdatedAt(LocalDateTime.now());

        ReflectionTestUtils.invokeMethod(service, "enqueueCaseUpdateWebhook", cCase);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webhookOutboxService).enqueueForPsp(
                eq(2L),
                eq("CASE_UPDATE"),
                payloadCaptor.capture(),
                eq("webhook.case_update:99:" + cCase.getUpdatedAt()));
        assertEquals(99L, payloadCaptor.getValue().get("caseId"));
        assertEquals("IN_PROGRESS", payloadCaptor.getValue().get("status"));
    }
}
