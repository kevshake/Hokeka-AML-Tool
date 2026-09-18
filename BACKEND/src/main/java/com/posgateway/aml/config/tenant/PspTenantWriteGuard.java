package com.posgateway.aml.config.tenant;

import com.posgateway.aml.config.RlsContextHolder;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Blocks cross-tenant {@code save} / {@code saveAll} when the entity carries a {@code psp_id}
 * that does not match the request thread-local tenant context.
 */
@Aspect
@Component
public class PspTenantWriteGuard {

    @Before("execution(* org.springframework.data.jpa.repository.JpaRepository+.save*(..)) && args(entity)")
    public void guardSingleSave(Object entity) {
        validate(entity);
    }

    @Before("execution(* org.springframework.data.jpa.repository.JpaRepository+.saveAll(..)) && args(entities)")
    public void guardBulkSave(Iterable<?> entities) {
        if (entities == null) {
            return;
        }
        for (Object entity : entities) {
            validate(entity);
        }
    }

    private void validate(Object entity) {
        long ctxPspId = PspTenantFilterSupport.resolveFilterPspId();
        if (ctxPspId == PspTenantFilter.PLATFORM_ADMIN_PSP_ID) {
            return;
        }
        Long entityPspId = PspTenantFilterSupport.extractEntityPspId(entity);
        if (entityPspId != null && entityPspId != ctxPspId) {
            throw new SecurityException(
                    "Cross-tenant write blocked: entity psp_id=" + entityPspId + " does not match tenant " + ctxPspId);
        }
    }
}
