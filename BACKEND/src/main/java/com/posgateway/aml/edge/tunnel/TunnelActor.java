package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * Who the Control Plane acts for in one tunnel request ({@code common.schema.json#/$defs/actor},
 * §7.5). Built by the calling cloud code (R6) from the authenticated user.
 *
 * @param kind          {@code PSP_USER}, {@code PLATFORM_USER} or {@code SYSTEM}
 * @param userId        cloud user id, or {@code system:<job>} for SYSTEM
 * @param role          role name ({@code ^[A-Z][A-Z0-9_]{1,63}$})
 * @param pspId         PSP whose data is read; must equal the Edge's PSP
 * @param purpose       one of the §7.5 purposes
 * @param caseId        optional workflow reference
 * @param alertId       optional workflow reference
 * @param filingRef     optional workflow reference
 * @param supportReason REQUIRED for PLATFORM_USER (10..500 chars, §9), absent otherwise
 */
public record TunnelActor(Kind kind, String userId, String role, long pspId, Purpose purpose,
                          String caseId, String alertId, String filingRef, String supportReason) {

    /** Actor kinds (§7.5). */
    public enum Kind { PSP_USER, PLATFORM_USER, SYSTEM }

    /** Request purposes (§7.5). */
    public enum Purpose {
        ALERT_REVIEW, CASE_INVESTIGATION, SAR_FILING, CTR_FILING, TRANSACTION_MONITORING,
        CUSTOMER_REVIEW, NETWORK_ANALYSIS, DATA_EXPORT, SUPPORT_ACCESS, LEDGER_BACKFILL, LEDGER_OPERATIONS
    }

    /**
     * Roles that make a user Hokeka platform staff, as {@code PspIsolationService
     * .isPlatformAdministrator} decides (ADMIN only on the platform sentinel PSP, which the caller
     * resolves before choosing {@link Kind#PLATFORM_USER}).
     */
    public static final Set<String> PLATFORM_ROLES = Set.of("SUPER_ADMIN", "PLATFORM_ADMIN", "APP_CONTROLLER", "ADMIN");

    /** Convenience constructor for a PSP user. */
    public static TunnelActor pspUser(String userId, String role, long pspId, Purpose purpose, String caseId) {
        return new TunnelActor(Kind.PSP_USER, userId, role, pspId, purpose, caseId, null, null, null);
    }

    /** Convenience constructor for a platform user pulling under support access (§9). */
    public static TunnelActor platformUser(String userId, String role, long pspId, String supportReason) {
        return new TunnelActor(Kind.PLATFORM_USER, userId, role, pspId, Purpose.SUPPORT_ACCESS,
                null, null, null, supportReason);
    }

    /** Convenience constructor for a SYSTEM job ({@code userId = system:<job>}). */
    public static TunnelActor system(String job, long pspId, Purpose purpose) {
        return new TunnelActor(Kind.SYSTEM, "system:" + job, "SYSTEM", pspId, purpose, null, null, null, null);
    }

    /** Copy of this actor whose purpose is replaced (used when the caller passes purpose separately). */
    public TunnelActor withPurpose(Purpose p) {
        return new TunnelActor(kind, userId, role, pspId, p, caseId, alertId, filingRef, supportReason);
    }

    /** Renders the {@code actor} object of a request body. */
    public ObjectNode toJson() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("kind", kind.name());
        n.put("userId", userId);
        n.put("role", role);
        n.put("pspId", pspId);
        n.put("purpose", purpose.name());
        if (caseId != null) {
            n.put("caseId", caseId);
        }
        if (alertId != null) {
            n.put("alertId", alertId);
        }
        if (filingRef != null) {
            n.put("filingRef", filingRef);
        }
        if (supportReason != null) {
            n.put("supportReason", supportReason);
        }
        return n;
    }
}
