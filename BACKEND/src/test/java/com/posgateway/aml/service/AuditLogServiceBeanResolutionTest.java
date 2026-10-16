package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.security.PaymentBlacklistRepository;
import com.posgateway.aml.service.cache.FeatureCacheService;
import com.posgateway.aml.service.compliance.EnhancedAuditService;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.security.PaymentBlacklistAdminService;
import com.posgateway.aml.service.security.PiiLookupHasher;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression (sim run 2026-10-16): the production Control Plane failed to boot because
 * {@link PaymentBlacklistAdminService} injects {@code AuditLogService audit} and the context holds two
 * beans of that type ({@link AuditLogService} and its subclass {@link EnhancedAuditService}). This test
 * builds a real Spring context with both audit beans and asserts that an injection point whose
 * parameter name matches neither bean resolves, deterministically, to the base service.
 */
class AuditLogServiceBeanResolutionTest {

    @Test
    void auditLogServiceInjectionIsUnambiguousWhenEnhancedSubclassIsPresent() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(AuditLogRepository.class, () -> mock(AuditLogRepository.class));
            ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            ctx.registerBean(KafkaOutboxService.class, () -> mock(KafkaOutboxService.class));
            ctx.registerBean(PaymentBlacklistRepository.class, () -> mock(PaymentBlacklistRepository.class));
            ctx.registerBean(FeatureCacheService.class, () -> mock(FeatureCacheService.class));
            ctx.registerBean(PspIsolationService.class, () -> mock(PspIsolationService.class));
            ctx.registerBean(PiiLookupHasher.class, () -> mock(PiiLookupHasher.class));
            ctx.register(AuditLogService.class, EnhancedAuditService.class, PaymentBlacklistAdminService.class);
            ctx.refresh();

            Object injected = ReflectionTestUtils.getField(ctx.getBean(PaymentBlacklistAdminService.class), "audit");
            assertThat(injected).isNotNull();
            assertThat(injected.getClass()).isEqualTo(AuditLogService.class);
            // Consumers asking for the subclass by type still get it.
            assertThat(ctx.getBean(EnhancedAuditService.class)).isInstanceOf(EnhancedAuditService.class);
        }
    }
}
