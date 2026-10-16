package com.hokeka.edge.tunnel;

import com.hokeka.edge.EdgeController;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.activation.AuthorizationGate;
import com.hokeka.edge.channel.EdgeMetricsAggregator;
import com.hokeka.edge.ledger.EvaluationRecorder;
import com.hokeka.edge.store.NoOpFeatureStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /edge/status} surfaces the R2/R5 state (contract §7, plan "Surface tunnel state"): the
 * config in force, effective retention and the last purge, and the tunnel client's state — ids and
 * counts only.
 */
class EdgeStatusResidencyTest {

    private TunnelRig rig;

    @BeforeEach
    void setUp() {
        rig = new TunnelRig();
    }

    @AfterEach
    void tearDown() {
        rig.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    void statusReportsConfigRetentionAndTunnel() {
        EdgeController controller = new EdgeController(new EdgeEngine(AuthorizationGate.alwaysAuthorized()),
                new EdgeMetricsAggregator("12", TunnelRig.EDGE_ID, 64, 1_000L), new NoOpFeatureStore(),
                new EvaluationRecorder(rig.ledger, rig.schemas, rig.mapper), TunnelRig.EDGE_ID, TunnelRig.SITE_ID);
        controller.setLocalResidencyStatus(rig.config, rig.purger, rig.ledgerProperties);

        Map<String, Object> before = controller.status();
        Map<String, Object> retention = (Map<String, Object>) before.get("retention");
        assertEquals(false, retention.get("purging"), "no purge before the first config");
        assertNull(((Map<String, Object>) before.get("edgeConfig")).get("configVersion"));
        assertEquals("NOT_CONFIGURED", ((Map<String, Object>) before.get("tunnel")).get("state"));

        rig.acceptConfig(14, null);
        rig.purger.purgeNow();
        EdgeTunnelClient client = new EdgeTunnelClient(new TunnelProperties(), null,
                new EdgeTunnelClient.Identity(TunnelRig.EDGE_ID, TunnelRig.SITE_ID, TunnelRig.PSP_ID),
                () -> {
                    throw new IllegalStateException("CONTROLPLANE_X25519_PUBLIC_KEY is not set");
                }, () -> {
                    throw new IllegalStateException("EDGE_MTLS_KEYSTORE is not set");
                }, AuthorizationGate.alwaysAuthorized(), rig.codec, rig.ops, rig.accessLog, rig.config, rig.holds,
                new EdgeTunnelClient.Signals(() -> { }, () -> { }, () -> null, () -> -1L, () -> 2), rig.mapper,
                Clock.systemUTC());
        controller.setResidencyStatus(client, rig.config, null, rig.purger, rig.ledgerProperties);

        Map<String, Object> after = controller.status();
        Map<String, Object> cfg = (Map<String, Object>) after.get("edgeConfig");
        assertEquals(14L, cfg.get("configVersion"));
        assertEquals(false, cfg.get("supportAccess"));
        assertEquals(1, cfg.get("merchantBlocklistEntries"));
        assertEquals("2026-10-16.1", cfg.get("sanctionsListVersion"));
        Map<String, Object> ret = (Map<String, Object>) after.get("retention");
        assertEquals(5, ret.get("effectiveYears"));
        assertEquals("OK", ret.get("lastOutcome"));
        assertTrue(Instant.parse((String) ret.get("nextPurgeAt")).isAfter(Instant.now().minusSeconds(86_400 * 2)));
        Map<String, Object> tunnel = (Map<String, Object>) after.get("tunnel");
        assertEquals("STOPPED", tunnel.get("state"));
        assertEquals(0L, tunnel.get("requestsServed"));
        client.stop();
    }
}
