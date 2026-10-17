package com.posgateway.aml.service.edgedata;

import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the authenticated principal into the PSP and tunnel actor of one Console call (R6).
 *
 * <p><b>Tenant isolation.</b> A PSP user always reads its own PSP: the PSP comes from the principal.
 * A {@code pspId} parameter that names another PSP is refused with {@code 403 TENANT_MISMATCH}
 * before anything is read. Platform staff must name the PSP and act as {@code PLATFORM_USER} with
 * purpose {@code SUPPORT_ACCESS} and a reason; the gateway's §9 gate (support access enabled,
 * 10..500 character reason, platform role) refuses everything else with
 * {@code 403 SUPPORT_ACCESS_DISABLED}, writing the audit row either way.
 */
@Service
public class EdgeDataCallerResolver {

    /** Workflow references attached to the actor (and so to both audit rows). */
    public record WorkflowRefs(String caseId, String alertId, String filingRef) {

        /** No workflow reference. */
        public static final WorkflowRefs NONE = new WorkflowRefs(null, null, null);

        /** A case investigation reference. */
        public static WorkflowRefs ofCase(Object caseId) {
            return new WorkflowRefs(caseId == null ? null : String.valueOf(caseId), null, null);
        }

        /** An alert review reference. */
        public static WorkflowRefs ofAlert(Object alertId) {
            return new WorkflowRefs(null, alertId == null ? null : String.valueOf(alertId), null);
        }

        /** A filing (SAR/STR/CTR) reference. */
        public static WorkflowRefs ofFiling(Object filingRef) {
            return new WorkflowRefs(null, null, filingRef == null ? null : String.valueOf(filingRef));
        }
    }

    /** Purposes a PSP user may name (§7.5: never support access or ledger jobs). */
    private static final Set<TunnelActor.Purpose> PSP_USER_PURPOSES = EnumSet.of(
            TunnelActor.Purpose.ALERT_REVIEW, TunnelActor.Purpose.CASE_INVESTIGATION, TunnelActor.Purpose.SAR_FILING,
            TunnelActor.Purpose.CTR_FILING, TunnelActor.Purpose.TRANSACTION_MONITORING,
            TunnelActor.Purpose.CUSTOMER_REVIEW, TunnelActor.Purpose.NETWORK_ANALYSIS, TunnelActor.Purpose.DATA_EXPORT);

    private static final Pattern USER_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._@:-]{0,127}$");
    private static final Pattern ROLE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    private static final Pattern WORKFLOW_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");

    private final PspIsolationService isolation;

    /** Uses the platform's tenant-isolation service as the source of identity. */
    public EdgeDataCallerResolver(PspIsolationService isolation) {
        this.isolation = isolation;
    }

    /**
     * The current caller.
     *
     * @throws AccessDeniedException when nobody is authenticated
     * @throws EdgeDataException     {@code 403 TENANT_MISMATCH} for a non-platform user without a PSP
     */
    public EdgeDataCaller current() {
        User user = isolation.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("authentication required");
        }
        boolean platform = isolation.isPlatformAdministrator(user);
        Long pspId = null;
        if (!platform) {
            pspId = user.getPsp() == null ? null : user.getPsp().getPspId();
            if (pspId == null || pspId <= 0) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH,
                        "the caller is not bound to a PSP"));
            }
        }
        return new EdgeDataCaller(userRef(user), roleOf(user), pspId, platform);
    }

    /**
     * The PSP and actor of one call.
     *
     * @param requestedPspId the {@code pspId} parameter: must be absent or the caller's own PSP for
     *                       PSP users; required for platform staff
     * @param supportReason  platform staff only: the §9 reason for this pull
     * @param purpose        the endpoint's purpose for PSP users (platform pulls are always
     *                       {@code SUPPORT_ACCESS})
     * @param refs           workflow references for the audit rows
     */
    public EdgeDataScope scope(Long requestedPspId, String supportReason, TunnelActor.Purpose purpose,
                               WorkflowRefs refs) {
        return scopeFor(current(), requestedPspId, supportReason, purpose, refs);
    }

    /** {@link #scope} for an already resolved caller. */
    public EdgeDataScope scopeFor(EdgeDataCaller caller, Long requestedPspId, String supportReason,
                                  TunnelActor.Purpose purpose, WorkflowRefs refs) {
        WorkflowRefs r = refs == null ? WorkflowRefs.NONE : refs;
        if (caller.pspUser()) {
            long own = caller.pspId();
            if (requestedPspId != null && requestedPspId != own) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH,
                        "a PSP user can only read its own PSP"));
            }
            if (!PSP_USER_PURPOSES.contains(purpose)) {
                throw new EdgeDataException(TunnelError.invalidParams("/purpose"));
            }
            TunnelActor actor = new TunnelActor(TunnelActor.Kind.PSP_USER, caller.userRef(), caller.role(), own,
                    purpose, ref(r.caseId()), ref(r.alertId()), ref(r.filingRef()), null);
            return new EdgeDataScope(own, actor, caller);
        }
        if (requestedPspId == null || requestedPspId <= 0) {
            throw new EdgeDataException(TunnelError.invalidParams("/pspId"));
        }
        String reason = supportReason == null || supportReason.isBlank() ? null : supportReason.strip();
        TunnelActor actor = new TunnelActor(TunnelActor.Kind.PLATFORM_USER, caller.userRef(), caller.role(),
                requestedPspId, TunnelActor.Purpose.SUPPORT_ACCESS, ref(r.caseId()), ref(r.alertId()),
                ref(r.filingRef()), reason);
        return new EdgeDataScope(requestedPspId, actor, caller);
    }

    /**
     * The scope of a pull made on behalf of a workflow record owned by {@code recordPspId} (alert,
     * case, SAR): a PSP user must own the record; platform staff read it under support access.
     */
    public EdgeDataScope scopeForRecord(EdgeDataCaller caller, long recordPspId, String supportReason,
                                        TunnelActor.Purpose purpose, WorkflowRefs refs) {
        if (caller.pspUser() && caller.pspId() != recordPspId) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH,
                    "the record belongs to another PSP"));
        }
        return scopeFor(caller, recordPspId, supportReason, purpose, refs);
    }

    /** Cloud user reference: {@code u-<id>} for stored users, else the sanitised username. */
    static String userRef(User user) {
        if (user.getId() != null) {
            return "u-" + user.getId();
        }
        String name = user.getUsername() == null ? "" : user.getUsername().replaceAll("[^A-Za-z0-9._@:-]", "_");
        if (name.isEmpty() || !Character.isLetterOrDigit(name.charAt(0))) {
            name = "u" + name;
        }
        if (name.length() > 128) {
            name = name.substring(0, 128);
        }
        return USER_REF.matcher(name).matches() ? name : "unknown-user";
    }

    /** Role name in the actor's pattern; a role that cannot be expressed becomes {@code USER}. */
    static String roleOf(User user) {
        String name = user.getRole() == null || user.getRole().getName() == null ? "" : user.getRole().getName();
        String upper = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (upper.startsWith("ROLE_")) {
            upper = upper.substring(5);
        }
        if (upper.length() > 64) {
            upper = upper.substring(0, 64);
        }
        return ROLE.matcher(upper).matches() ? upper : "USER";
    }

    /** A workflow reference when it fits {@code workflowRef}, else nothing (never a malformed id). */
    private static String ref(String value) {
        return value != null && WORKFLOW_REF.matcher(value).matches() ? value : null;
    }
}
