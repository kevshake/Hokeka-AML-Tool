package com.posgateway.aml.entity.compliance;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the fix for W18-1: createdAt is a NOT NULL column but nothing ever stamped it before a
 * save -- any programmatic insert (not just the Flyway-seeded rows this entity currently gets)
 * would have violated the NOT NULL constraint at insert time. Same @PrePersist pattern as the
 * sibling compliance entities.
 */
class RegulatoryDeadlinePolicyTest {

    @Test
    void prePersistStampsCreatedAtWhenAbsent() throws Exception {
        RegulatoryDeadlinePolicy policy = new RegulatoryDeadlinePolicy();
        assertNotNullAfterPrePersist(policy);
    }

    @Test
    void prePersistDoesNotOverwriteAnAlreadySetCreatedAt() throws Exception {
        RegulatoryDeadlinePolicy policy = new RegulatoryDeadlinePolicy();
        LocalDateTime explicit = LocalDateTime.of(2020, 1, 1, 0, 0);
        setCreatedAt(policy, explicit);

        invokeOnCreate(policy);

        assertEquals(explicit, getCreatedAt(policy));
    }

    private void assertNotNullAfterPrePersist(RegulatoryDeadlinePolicy policy) throws Exception {
        invokeOnCreate(policy);
        assertNotNull(getCreatedAt(policy));
    }

    private void invokeOnCreate(RegulatoryDeadlinePolicy policy) throws Exception {
        Method m = RegulatoryDeadlinePolicy.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(policy);
    }

    private void setCreatedAt(RegulatoryDeadlinePolicy policy, LocalDateTime value) throws Exception {
        Field f = RegulatoryDeadlinePolicy.class.getDeclaredField("createdAt");
        f.setAccessible(true);
        f.set(policy, value);
    }

    private LocalDateTime getCreatedAt(RegulatoryDeadlinePolicy policy) {
        return policy.getCreatedAt();
    }
}
