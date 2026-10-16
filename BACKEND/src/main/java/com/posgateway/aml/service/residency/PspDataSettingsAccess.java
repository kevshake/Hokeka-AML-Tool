package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Who may read and change a PSP's data residency settings (contract sections 8.1, 9 and 10).
 *
 * <ul>
 *   <li><b>Read:</b> any user of that PSP, or a platform operator.</li>
 *   <li><b>Maker (propose a change):</b> a PSP admin of that PSP. Platform operators cannot propose:
 *       retention, support access and cross-PSP opt-in are the PSP's decisions (I6).</li>
 *   <li><b>Checker (approve / reject):</b> a PSP admin, MLRO or compliance officer of that PSP who
 *       is not the maker.</li>
 *   <li><b>Edge config editor</b> (CTR, tunnel, limits, blocklist): a PSP admin of that PSP or a
 *       platform operator.</li>
 * </ul>
 */
@Component
public class PspDataSettingsAccess {

    /** Tenant roles that may propose a change to their own PSP's settings. */
    static final Set<String> MAKER_ROLES = Set.of("PSP_ADMIN", "ADMIN");

    /** Tenant roles that may approve or reject a change proposed by someone else. */
    static final Set<String> CHECKER_ROLES = Set.of("PSP_ADMIN", "ADMIN", "MLRO", "COMPLIANCE_OFFICER");

    private final PspIsolationService isolation;

    public PspDataSettingsAccess(PspIsolationService isolation) {
        this.isolation = isolation;
    }

    /** The authenticated user, or a SecurityException (403) when there is none. */
    public User currentUser() {
        User user = isolation.getCurrentUser();
        if (user == null) {
            throw new SecurityException("Authentication required");
        }
        return user;
    }

    /** Returns the caller when they may read {@code pspId}'s settings; throws SecurityException otherwise. */
    public User requireRead(Long pspId) {
        User user = currentUser();
        if (isolation.isPlatformAdministrator(user)) {
            return user;
        }
        requireOwnPsp(user, pspId);
        return user;
    }

    /** Returns the caller when they may propose a change to {@code pspId}'s settings. */
    public User requireMaker(Long pspId) {
        User user = currentUser();
        if (isolation.isPlatformAdministrator(user)) {
            throw new SecurityException("Data retention, support access and cross-PSP sharing are changed by the PSP's own administrators");
        }
        requireOwnPsp(user, pspId);
        if (!MAKER_ROLES.contains(roleOf(user))) {
            throw new SecurityException("Only a PSP administrator can propose this change");
        }
        return user;
    }

    /** Returns the caller when they may approve or reject a change to {@code pspId}'s settings. */
    public User requireChecker(Long pspId) {
        User user = currentUser();
        if (isolation.isPlatformAdministrator(user)) {
            throw new SecurityException("Changes to PSP data settings are approved by the PSP itself");
        }
        requireOwnPsp(user, pspId);
        if (!CHECKER_ROLES.contains(roleOf(user))) {
            throw new SecurityException("Only a PSP administrator, MLRO or compliance officer can review this change");
        }
        return user;
    }

    /** Returns the caller when they may edit {@code pspId}'s Edge CTR / tunnel / limit / blocklist config. */
    public User requireConfigEditor(Long pspId) {
        User user = currentUser();
        if (isolation.isPlatformAdministrator(user)) {
            return user;
        }
        requireOwnPsp(user, pspId);
        if (!MAKER_ROLES.contains(roleOf(user))) {
            throw new SecurityException("Only a PSP administrator can change the Edge configuration");
        }
        return user;
    }

    /** Throws unless the (tenant) user belongs to {@code pspId}. */
    private void requireOwnPsp(User user, Long pspId) {
        Long own = user.getPsp() == null ? null : user.getPsp().getPspId();
        if (pspId == null || own == null || !own.equals(pspId)) {
            throw new SecurityException("Cannot access data from another PSP");
        }
    }

    /** Upper-case role name, or "" when the user has none. */
    static String roleOf(User user) {
        if (user == null || user.getRole() == null || user.getRole().getName() == null) {
            return "";
        }
        return user.getRole().getName().trim().toUpperCase(Locale.ROOT);
    }
}
