package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.service.residency.EdgeTunnelSettingsService;

import java.util.Set;

/**
 * {@link TunnelPspSettingsProvider} backed by the PSP's sealed Edge config document (work package
 * R3, contract §6.1 {@code tunnel} section), so the tunnel enforces exactly what the PSP's Edges
 * were told: {@code tunnel.enabled} (4403 at hello and on re-check), {@code tunnel.opsAllowed}
 * ({@code hello.ack.opsEffective}), {@code tunnel.backfillAllowed},
 * {@code tunnel.maxInflightPerSession} and the {@code configVersion} in force.
 *
 * <p>A PSP without stored edge settings gets the §6.1 defaults from R3's document builder, so no
 * separate default provider exists. Errors (unknown PSP, database down) propagate: the session hub
 * refuses the handshake rather than guessing settings.
 */
public class ResidencyTunnelPspSettings implements TunnelPspSettingsProvider {

    private final EdgeTunnelSettingsService settingsService;

    /** Adapts R3's tunnel settings service. */
    public ResidencyTunnelPspSettings(EdgeTunnelSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** The tunnel section of the PSP's current config document, mapped to the tunnel's record. */
    @Override
    public TunnelPspSettings settings(long pspId) {
        EdgeTunnelSettingsService.TunnelSettings s = settingsService.settingsFor(pspId);
        return new TunnelPspSettings(s.enabled(), Set.copyOf(s.opsAllowed()), s.backfillAllowed(),
                s.maxInflightPerSession(), s.configVersion());
    }
}
