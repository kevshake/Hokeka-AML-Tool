package com.posgateway.aml.service.edgedata;

/**
 * The authenticated user behind one Console call to a tunnel-backed endpoint (work package R6),
 * reduced to what a tunnel actor needs (contract §7.5).
 *
 * @param userRef  cloud user reference ({@code common.schema.json#/$defs/userRef})
 * @param role     role name ({@code ^[A-Z][A-Z0-9_]{1,63}$})
 * @param pspId    the caller's own PSP; {@code null} for platform staff
 * @param platform true for Hokeka platform staff ({@code PspIsolationService.isPlatformAdministrator})
 */
public record EdgeDataCaller(String userRef, String role, Long pspId, boolean platform) {

    /** True for a user bound to one PSP (the PSP comes from the principal, never a parameter). */
    public boolean pspUser() {
        return !platform;
    }
}
