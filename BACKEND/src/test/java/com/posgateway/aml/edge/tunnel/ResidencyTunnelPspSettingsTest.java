package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.service.residency.EdgeTunnelSettingsService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tunnel reads R3's per-PSP {@code tunnel} config section: mapping, error propagation, and
 * the §6.1 default ops of R3 matching the tunnel's own op allowlist.
 */
class ResidencyTunnelPspSettingsTest {

    /** Verifies: every field of R3's settings reaches the tunnel record. */
    @Test
    void mapsTheConfigDocumentsTunnelSection() {
        EdgeTunnelSettingsService service = mock(EdgeTunnelSettingsService.class);
        when(service.settingsFor(9L)).thenReturn(new EdgeTunnelSettingsService.TunnelSettings(
                false, List.of("txn.get", "ledger.backfill"), true, 4, 12L));

        TunnelPspSettingsProvider.TunnelPspSettings s = new ResidencyTunnelPspSettings(service).settings(9L);

        assertThat(s.tunnelEnabled()).isFalse();
        assertThat(s.opsAllowed()).containsExactlyInAnyOrder("txn.get", "ledger.backfill");
        assertThat(s.backfillAllowed()).isTrue();
        assertThat(s.maxInflightPerSession()).isEqualTo(4);
        assertThat(s.configVersion()).isEqualTo(12L);
    }

    /** Verifies: a lookup failure propagates (the hub then refuses the handshake). */
    @Test
    void lookupFailuresPropagate() {
        EdgeTunnelSettingsService service = mock(EdgeTunnelSettingsService.class);
        when(service.settingsFor(9L)).thenThrow(new IllegalStateException("PSP not found"));
        assertThatThrownBy(() -> new ResidencyTunnelPspSettings(service).settings(9L))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Verifies: R3's §6.1 default ops are exactly the tunnel's read ops (every op but backfill). */
    @Test
    void r3DefaultOpsAreEveryReadOpOfTheTunnelAllowlist() {
        Set<String> readOps = Arrays.stream(TunnelOp.values())
                .filter(op -> op != TunnelOp.LEDGER_BACKFILL)
                .map(TunnelOp::wire)
                .collect(Collectors.toSet());
        assertThat(Set.copyOf(PspEdgeSettings.DEFAULT_TUNNEL_OPS)).isEqualTo(readOps);
        assertThat(PspEdgeSettings.DEFAULT_TUNNEL_OPS).doesNotHaveDuplicates();
    }

    /** Verifies: the hub's opsEffective rule (offer ∩ allowed, backfill only with backfillAllowed). */
    @Test
    void effectiveOpsIntersectOfferAndConfig() {
        Set<String> offered = new java.util.LinkedHashSet<>(List.of("txn.get", "txn.search", "ledger.backfill"));
        TunnelPspSettingsProvider.TunnelPspSettings noBackfill = new TunnelPspSettingsProvider.TunnelPspSettings(
                true, Set.of("txn.get", "ledger.backfill", "ledger.health"), false, 16, 1L);
        assertThat(TunnelSessionHub.effectiveOps(offered, noBackfill)).containsExactly("txn.get");
        TunnelPspSettingsProvider.TunnelPspSettings backfill = new TunnelPspSettingsProvider.TunnelPspSettings(
                true, Set.of("txn.get", "ledger.backfill"), true, 16, 1L);
        assertThat(TunnelSessionHub.effectiveOps(offered, backfill)).containsExactly("txn.get", "ledger.backfill");
    }
}
