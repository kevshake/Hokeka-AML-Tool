package com.posgateway.aml.service.backfill;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Who may act on a PSP's legacy back-fill (contract §9, §11; owner rule: platform staff act on a
 * PSP's data only with the PSP's support-access permission and an audited reason).
 *
 * <p>The contract makes the back-fill <b>platform-initiated</b>: a platform admin chooses the target
 * site and starts the run, which then talks to the Edge as the {@code SYSTEM} actor
 * {@code system:backfill} with purpose {@code LEDGER_BACKFILL} (never as a platform user). The PSP's
 * consent is threefold and every part is enforced: its Edge config allows the op
 * ({@code tunnel.backfillAllowed} and {@code ledger.backfill} in {@code opsAllowed}, checked by the
 * cloud and the Edge), its local {@code EDGE_TUNNEL_OPS} offers the op (enforced by the Edge), and
 * its support access is enabled (checked here for every platform action).
 *
 * <ul>
 *   <li><b>start, resume:</b> platform operators only, with support access enabled and a reason.</li>
 *   <li><b>pause, abort, resolve rejected rows, read the report:</b> platform operators (support
 *       access + reason), or the PSP's own administrators and compliance roles (PSP sovereignty, I6;
 *       a PSP still gives a reason for actions, which is audited).</li>
 * </ul>
 */
@Component
public class LedgerBackfillAccess {

    /** PSP roles that may pause, abort, resolve and read. */
    static final Set<String> PSP_ROLES = Set.of("PSP_ADMIN", "ADMIN", "MLRO", "COMPLIANCE_OFFICER");

    private final PspIsolationService isolation;
    private final EdgeTunnelAccess tunnel;

    /** Uses the platform's identity and the §9 support-access policy. */
    public LedgerBackfillAccess(PspIsolationService isolation, EdgeTunnelAccess tunnel) {
        this.isolation = isolation;
        this.tunnel = tunnel;
    }

    /** The caller of one back-fill action. */
    public record Caller(User user, String name, boolean platform) {}

    /** Start / resume: platform operator with the PSP's support access and a reason. */
    public Caller requirePlatformOperator(long pspId, String reason) {
        User user = current();
        if (!isolation.isPlatformAdministrator(user)) {
            throw new SecurityException("A legacy back-fill is started and resumed by Hokeka platform operators");
        }
        requireSupportAccess(pspId);
        requireReason(reason);
        return new Caller(user, user.getUsername(), true);
    }

    /** Pause / abort / resolve: platform (support access + reason) or the PSP's own admins (reason). */
    public Caller requireOperatorOrPsp(long pspId, String reason) {
        Caller c = requireReader(pspId, reason);
        requireReason(reason);
        return c;
    }

    /**
     * Read the report: platform (support access + reason) or a PSP admin / compliance user of that
     * PSP (no reason needed: it is their own data).
     */
    public Caller requireReader(long pspId, String reason) {
        User user = current();
        if (isolation.isPlatformAdministrator(user)) {
            requireSupportAccess(pspId);
            requireReason(reason);
            return new Caller(user, user.getUsername(), true);
        }
        Long own = user.getPsp() == null ? null : user.getPsp().getPspId();
        if (own == null || own != pspId) {
            throw new SecurityException("Cannot access data from another PSP");
        }
        if (!PSP_ROLES.contains(role(user))) {
            throw new SecurityException("Only a PSP administrator, MLRO or compliance officer can manage the back-fill");
        }
        return new Caller(user, user.getUsername(), false);
    }

    /** §9: the PSP has switched support access on. */
    private void requireSupportAccess(long pspId) {
        if (!tunnel.supportAccess().isSupportAccessEnabled(pspId)) {
            throw new SecurityException("Support access is not enabled for this PSP");
        }
    }

    /** A 10..500 character reason (the §9 support-reason rule). */
    static void requireReason(String reason) {
        String r = reason == null ? "" : reason.strip();
        if (r.length() < 10 || r.length() > 500) {
            throw new IllegalArgumentException("reason must be 10 to 500 characters");
        }
    }

    /** The authenticated user. */
    private User current() {
        User user = isolation.getCurrentUser();
        if (user == null) {
            throw new SecurityException("Authentication required");
        }
        return user;
    }

    /** Upper-case role name. */
    private static String role(User user) {
        return user.getRole() == null || user.getRole().getName() == null ? ""
                : user.getRole().getName().trim().toUpperCase(Locale.ROOT);
    }
}
