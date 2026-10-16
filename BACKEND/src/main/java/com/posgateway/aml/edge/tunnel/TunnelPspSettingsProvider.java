package com.posgateway.aml.edge.tunnel;

import java.util.Set;

/**
 * The per-PSP tunnel section of the Edge config document (§6.1 {@code tunnel}) plus the config
 * version in force, as the handshake needs them: {@code hello.ack.opsEffective} =
 * {@code hello.opsEnabled} ∩ {@code tunnel.opsAllowed}, {@code maxInflight} ≤
 * {@code tunnel.maxInflightPerSession}, {@code currentConfigVersion}, and close 4403 when the
 * config disables the tunnel.
 *
 * <p>Work package R3 owns the config document; its provider bean is expected to be
 * {@code @Primary}. Until a PSP has a stored config document, {@link ContractDefaultTunnelSettings}
 * answers with the §6.1 defaults.
 */
public interface TunnelPspSettingsProvider {

    /** Tunnel settings for {@code pspId}. */
    TunnelPspSettings settings(long pspId);

    /**
     * @param tunnelEnabled         {@code tunnel.enabled}
     * @param opsAllowed            {@code tunnel.opsAllowed} (wire op names)
     * @param backfillAllowed       {@code tunnel.backfillAllowed}
     * @param maxInflightPerSession {@code tunnel.maxInflightPerSession} (≤ 16)
     * @param configVersion         {@code configVersion} of the document in force (≥ 1)
     */
    record TunnelPspSettings(boolean tunnelEnabled, Set<String> opsAllowed, boolean backfillAllowed,
                             int maxInflightPerSession, long configVersion) {}
}
