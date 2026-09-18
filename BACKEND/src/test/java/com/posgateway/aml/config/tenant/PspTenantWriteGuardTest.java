package com.posgateway.aml.config.tenant;

import com.posgateway.aml.config.RlsContextHolder;
import com.posgateway.aml.entity.TransactionEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PspTenantWriteGuardTest {

    private final PspTenantWriteGuard guard = new PspTenantWriteGuard();

    @AfterEach
    void clearContext() {
        RlsContextHolder.clear();
    }

    @Test
    void blocksCrossTenantSave() {
        RlsContextHolder.setCurrentPspId(1L);
        TransactionEntity foreign = new TransactionEntity();
        foreign.setPspId(2L);

        assertThrows(SecurityException.class, () -> guard.guardSingleSave(foreign));
    }

    @Test
    void allowsSameTenantSave() {
        RlsContextHolder.setCurrentPspId(1L);
        TransactionEntity own = new TransactionEntity();
        own.setPspId(1L);

        assertDoesNotThrow(() -> guard.guardSingleSave(own));
    }

    @Test
    void platformAdminBypassesGuard() {
        TransactionEntity foreign = new TransactionEntity();
        foreign.setPspId(99L);

        assertDoesNotThrow(() -> guard.guardSingleSave(foreign));
    }
}
