package com.posgateway.aml.config.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Defense-in-depth: enable the Hibernate PSP tenant filter before every repository call so
 * {@code findById} and other unscoped queries cannot leak cross-tenant rows when application
 * code omits an explicit {@code psp_id} predicate.
 */
@Aspect
@Component
public class PspFilterEnabler {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.posgateway.aml.repository..*(..))")
    public void enableTenantFilter() {
        PspTenantFilterSupport.enable(entityManager.unwrap(Session.class));
    }
}
