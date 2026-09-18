package com.posgateway.aml.config.tenant;

import com.posgateway.aml.config.RlsContextHolder;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import org.hibernate.Session;

/**
 * Enables the shared PSP tenant filter on a Hibernate {@link Session}.
 */
public final class PspTenantFilterSupport {

    private PspTenantFilterSupport() {}

    /** Resolves the filter parameter: thread-local PSP id, or {@code 0} for platform / background. */
    public static long resolveFilterPspId() {
        Long ctx = RlsContextHolder.getCurrentPspId();
        return ctx != null ? ctx : PspTenantFilter.PLATFORM_ADMIN_PSP_ID;
    }

    public static void enable(Session session) {
        if (session == null) {
            return;
        }
        session.enableFilter(PspTenantFilter.NAME)
                .setParameter(PspTenantFilter.PARAM, resolveFilterPspId());
    }

    /**
     * Extracts {@code psp_id} from tenant-scoped entities for write guards.
     * Returns {@code null} when the entity is not PSP-scoped.
     */
    public static Long extractEntityPspId(Object entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof TransactionEntity txn) {
            return txn.getPspId();
        }
        if (entity instanceof Alert alert) {
            return alert.getPspId();
        }
        if (entity instanceof ComplianceCase complianceCase) {
            return complianceCase.getPspId();
        }
        if (entity instanceof User user) {
            Psp psp = user.getPsp();
            return psp != null ? psp.getPspId() : null;
        }
        if (entity instanceof Merchant merchant) {
            Psp psp = merchant.getPsp();
            return psp != null ? psp.getPspId() : null;
        }
        return null;
    }
}
