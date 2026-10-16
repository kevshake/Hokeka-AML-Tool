package com.posgateway.aml.edge.tunnel;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * §6.1 defaults of the {@code tunnel} config section: "enabled, all read ops, backfill off, 16",
 * for a PSP that has no stored Edge config document. The default document is version 1, the first
 * value {@code configVersion} can take; R3's stored documents increment from there.
 *
 * <p>"All read ops" is every op except the one write op, {@code ledger.backfill} (§7.6, §11:
 * backfill needs {@code backfillAllowed = true} and is off by default).
 */
public class ContractDefaultTunnelSettings implements TunnelPspSettingsProvider {

    /** Every read op of the §7.6 allowlist. */
    static final Set<String> READ_OPS = Arrays.stream(TunnelOp.values())
            .filter(op -> op != TunnelOp.LEDGER_BACKFILL)
            .map(TunnelOp::wire)
            .collect(Collectors.toUnmodifiableSet());

    private static final TunnelPspSettings DEFAULTS = new TunnelPspSettings(true, READ_OPS, false, 16, 1L);

    /** The same §6.1 default document for every PSP. */
    @Override
    public TunnelPspSettings settings(long pspId) {
        return DEFAULTS;
    }
}
