package com.hokeka.edge.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.channel.SealedEnvelopeCodec;
import com.hokeka.edge.contract.Jcs;
import com.hokeka.edge.crypto.EdgeKeys;
import com.hokeka.edge.ledger.LedgerTestDatabase;
import com.hokeka.edge.tunnel.CloudSide;
import com.hokeka.edge.tunnel.TunnelRig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Edge config consumer against a real ledger (contract §6.2, §6.3): the shared config fixtures,
 * verify-then-swap (schema, PSP, rollback, same-version conflicts), history in {@code edge_config}
 * before use, restore after restart, and the sealed envelope path of {@link EdgeConfigPoller}
 * (signature, context, replay, edgeId) using an independently implemented Control Plane seal.
 */
class EdgeConfigStoreIntegrationTest {

    private TunnelRig rig;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        rig = new TunnelRig();
    }

    @AfterEach
    void tearDown() {
        rig.close();
    }

    @Test
    void sharedFixturesAreAcceptedOrRefusedAsTheSchemaSays() {
        Instant now = rig.clock.instant();
        assertEquals(EdgeConfigStore.Outcome.ACCEPTED, rig.config.offer(rig.fixture("edge-config--default.json"), now)
                .outcome());
        EdgeConfigDocument d = rig.config.current().orElseThrow();
        assertEquals(14, d.configVersion());
        assertEquals("Africa/Nairobi", d.ctr().timezone().getId());
        assertEquals(new java.math.BigDecimal("0.0077"), d.ctr().rates().get("KES"));
        assertTrue(d.merchantBlocked("mrc_09917"));
        assertEquals(EdgeConfigStore.Outcome.UNCHANGED, rig.config.offer(rig.fixture("edge-config--default.json"), now)
                .outcome());
        for (String invalid : List.of("edge-config--float-fx-rate.invalid.json",
                "edge-config--retention-above-max.invalid.json", "edge-config--retention-below-floor.invalid.json")) {
            ObjectNode doc = rig.fixture(invalid);
            doc.put("configVersion", 99);
            assertEquals(EdgeConfigStore.Outcome.REFUSED, rig.config.offer(doc, now).outcome(), invalid);
        }
        assertEquals(EdgeConfigStore.Outcome.ACCEPTED,
                rig.config.offer(rig.fixture("edge-config--extended-retention.json"), now).outcome());
        EdgeConfigDocument ext = rig.config.current().orElseThrow();
        assertEquals(15, ext.configVersion());
        assertEquals(10, ext.retentionYears(), "the purge uses years in force, not pendingChange");
        assertTrue(ext.supportAccess());
    }

    @Test
    void rollbackOtherPspAndConflictingReissueAreRefused() throws Exception {
        Instant now = rig.clock.instant();
        rig.acceptConfig(20, null);
        assertEquals(EdgeConfigStore.Outcome.REFUSED, offer(19, d -> { }).outcome(), "rollback");
        assertEquals(EdgeConfigStore.Outcome.REFUSED, offer(21, d -> d.put("pspId", 13)).outcome(), "other PSP");
        assertEquals(EdgeConfigStore.Outcome.REFUSED,
                offer(20, d -> ((ObjectNode) d.get("supportAccess")).put("enabled", true)).outcome(),
                "same version, different content");
        assertEquals(20, rig.config.currentVersion().getAsLong(), "the previous document stays in force");
        // Unknown top-level members are ignored (§12.2); unknown members inside a section are not.
        assertEquals(EdgeConfigStore.Outcome.ACCEPTED, offer(21, d -> d.put("futureSection", 1)).outcome());
        assertEquals(EdgeConfigStore.Outcome.REFUSED,
                offer(22, d -> ((ObjectNode) d.get("tunnel")).put("futureFlag", true)).outcome());

        try (Connection c = LedgerTestDatabase.connect(rig.schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT config_version, psp_id, document_sha256 FROM edge_config ORDER BY 1")) {
            List<Long> versions = new ArrayList<>();
            while (rs.next()) {
                versions.add(rs.getLong(1));
                assertEquals(12, rs.getLong(2));
            }
            assertEquals(List.of(20L, 21L), versions, "every accepted version is in the history");
        }

        // A restarted node restores the newest stored version from the ledger.
        EdgeConfigStore restarted = new EdgeConfigStore(rig.ledger, rig.schemas, rig.mapper, TunnelRig.PSP_ID,
                TunnelRig.EDGE_ID);
        assertEquals(21, restarted.current().orElseThrow().configVersion());
        assertEquals(EdgeConfigStore.Outcome.REFUSED, restarted.offer(rig.fixture("edge-config--default.json"), now)
                .outcome(), "rollback protection survives a restart");

        EdgeConfigStore noPsp = new EdgeConfigStore(rig.ledger, rig.schemas, rig.mapper, null, TunnelRig.EDGE_ID);
        assertEquals(EdgeConfigStore.Outcome.REFUSED, noPsp.offer(rig.fixture("edge-config--extended-retention.json"),
                now).outcome());
        assertEquals(null, EdgeConfigStore.parsePspId("abc"));
        assertEquals(null, EdgeConfigStore.parsePspId("0"));
        assertEquals(12L, EdgeConfigStore.parsePspId(" 12 "));
    }

    private EdgeConfigStore.OfferResult offer(long version, java.util.function.Consumer<ObjectNode> edit) {
        ObjectNode doc = rig.fixture("edge-config--default.json");
        doc.put("configVersion", version);
        edit.accept(doc);
        return rig.config.offer(doc, rig.clock.instant());
    }

    @Test
    void pollerOpensTheSealedEnvelopeAndRefusesTamperedOnes() throws Exception {
        ControlPlaneProperties cp = new ControlPlaneProperties();
        cp.setEdgeId(TunnelRig.EDGE_ID);
        cp.setPspId("12");
        cp.setIdentityFile(tmp.resolve("identity.json").toString());
        ActivationService activation = new ActivationService(cp, Optional.empty(), new ObjectMapper(), new MockEnvironment());
        activation.loadOrCreateIdentity();
        KeyPair cpSigning = CloudSide.generate("Ed25519");
        SealedEnvelopeCodec codec = new SealedEnvelopeCodec(rig.mapper, new ReplayGuard());
        EdgeConfigPoller poller = new EdgeConfigPoller(cp, null, codec, activation, rig.config, rig.clock);
        byte[] edgeX25519 = EdgeKeys.decodeX25519PublicRaw(activation.loadOrCreateIdentity().x25519PublicKey());

        ObjectNode doc = rig.fixture("edge-config--default.json");
        byte[] good = seal(doc, TunnelRig.EDGE_ID, edgeX25519, cpSigning, "hokeka.edge.config", ReplayGuard.newNonce());
        assertEquals(EdgeConfigStore.Outcome.ACCEPTED, poller.apply(good, TunnelRig.EDGE_ID, cpSigning.getPublic(),
                rig.clock.instant()).outcome());
        assertEquals(EdgeConfigStore.Outcome.REFUSED, poller.apply(good, TunnelRig.EDGE_ID, cpSigning.getPublic(),
                rig.clock.instant()).outcome(), "a replayed envelope is refused");

        ObjectNode next = doc.deepCopy().put("configVersion", 15);
        KeyPair impostor = CloudSide.generate("Ed25519");
        assertEquals(EdgeConfigStore.Outcome.REFUSED, poller.apply(seal(next, TunnelRig.EDGE_ID, edgeX25519, impostor,
                "hokeka.edge.config", ReplayGuard.newNonce()), TunnelRig.EDGE_ID, cpSigning.getPublic(),
                rig.clock.instant()).outcome(), "wrong signing key");
        assertEquals(EdgeConfigStore.Outcome.REFUSED, poller.apply(seal(next, TunnelRig.EDGE_ID, edgeX25519, cpSigning,
                "hokeka.rules.bundle", ReplayGuard.newNonce()), TunnelRig.EDGE_ID, cpSigning.getPublic(),
                rig.clock.instant()).outcome(), "a bundle-context envelope is not a config");
        assertEquals(EdgeConfigStore.Outcome.REFUSED, poller.apply(seal(next, "acme-eu-2", edgeX25519, cpSigning,
                "hokeka.edge.config", ReplayGuard.newNonce()), TunnelRig.EDGE_ID, cpSigning.getPublic(),
                rig.clock.instant()).outcome(), "addressed to another node");
        assertEquals(14, rig.config.currentVersion().getAsLong());
        assertEquals(EdgeConfigStore.Outcome.ACCEPTED, poller.apply(seal(next, TunnelRig.EDGE_ID, edgeX25519, cpSigning,
                "hokeka.edge.config", ReplayGuard.newNonce()), TunnelRig.EDGE_ID, cpSigning.getPublic(),
                rig.clock.instant()).outcome());
        assertEquals(Jcs.sha256Hex(next), rig.config.current().orElseThrow().sha256());
    }

    /** The Control Plane's sealing of the config (EdgeConfigService.seal): replay envelope + HSE-1. */
    private byte[] seal(ObjectNode doc, String edgeId, byte[] edgeX25519, KeyPair signer, String context, String nonce) {
        ObjectNode env = rig.mapper.createObjectNode();
        env.put("nonce", nonce);
        env.put("issuedAt", rig.clock.instant().toString());
        env.put("edgeId", edgeId);
        env.set("payload", doc);
        return CloudSide.hseSeal(env.toString().getBytes(StandardCharsets.UTF_8), edgeX25519, signer.getPrivate(),
                context.getBytes(StandardCharsets.UTF_8));
    }
}
