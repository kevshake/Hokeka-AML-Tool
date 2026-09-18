package com.hokeka.edge.activation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.channel.ControlPlaneProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** First boot generates pinned keys, persists them owner-only, and stays fail-closed until ACTIVE. */
class ActivationServiceTest {

    private static final String BUNDLE = """
        { "version": 37, "psp_id": 42, "rules": [] }""";

    private static ControlPlaneProperties properties(Path identityFile) {
        ControlPlaneProperties p = new ControlPlaneProperties();
        p.setEnabled(true);
        p.setBaseUrl("https://edge.hokeka.com");
        p.setEdgeId("acme-eu-1");
        p.setPspId("acme");
        p.setIdentityFile(identityFile.toString());
        return p;
    }

    private static ActivationService service(ControlPlaneProperties properties) {
        return new ActivationService(properties, Optional.empty(), new ObjectMapper(), new MockEnvironment());
    }

    @Test
    void generatesAndPersistsPinnedCredentialsOnFirstBoot(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("edge-identity.json");
        ActivationService activation = service(properties(file));

        EdgeIdentity identity = activation.loadOrCreateIdentity();
        assertTrue(Files.isReadable(file));
        assertEquals("acme-eu-1", identity.edgeId());
        assertEquals(EdgeIdentity.STATUS_PENDING, identity.status());
        assertEquals("PKCS#8", activation.x25519Private().getFormat());
        assertEquals("PKCS#8", activation.ed25519Private().getFormat());

        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms);
        }
    }

    @Test
    void reloadsTheSameKeysOnRestart(@TempDir Path dir) {
        Path file = dir.resolve("edge-identity.json");
        EdgeIdentity first = service(properties(file)).loadOrCreateIdentity();
        EdgeIdentity second = service(properties(file)).loadOrCreateIdentity();

        assertEquals(first.x25519PublicKey(), second.x25519PublicKey());
        assertEquals(first.ed25519PrivateKey(), second.ed25519PrivateKey());
    }

    @Test
    void refusesCredentialsBelongingToAnotherEdge(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("edge-identity.json");
        service(properties(file)).loadOrCreateIdentity();
        Files.writeString(file, Files.readString(file).replace("acme-eu-1", "someone-else"));

        ActivationService activation = service(properties(file));
        SecurityException e = assertThrows(SecurityException.class, activation::loadOrCreateIdentity);
        assertTrue(e.getMessage().contains("pinned per edge id"), e.getMessage());
    }

    @Test
    void staysUnauthorizedUntilTheControlPlaneConfirms(@TempDir Path dir) {
        ActivationService activation = service(properties(dir.resolve("edge-identity.json")));
        assertFalse(activation.authorized());
        assertEquals(AuthorizationGate.STATE_UNAUTHORIZED, activation.state());

        // No channel bean and no enrollment code: it reports why instead of opening the gate.
        activation.ensureEnrolled();
        assertFalse(activation.authorized());
        assertFalse(activation.reason().isBlank());
    }

    @Test
    void engineHoldsUntilActivationSucceedsThenAllows(@TempDir Path dir) {
        ActivationService activation = service(properties(dir.resolve("edge-identity.json")));
        EdgeEngine engine = new EdgeEngine(activation);
        engine.loadPlainBundle(BUNDLE.getBytes(StandardCharsets.UTF_8));

        assertEquals(Action.HOLD, engine.evaluate(Map.of("amount", 10)).action());

        activation.loadOrCreateIdentity();
        activation.markActive();
        assertEquals(AuthorizationGate.STATE_ACTIVE, engine.authorizationState());
        assertEquals(Action.ALLOW, engine.evaluate(Map.of("amount", 10)).action());

        activation.markUnauthorized("revoked by the control plane");
        assertEquals(Action.HOLD, engine.evaluate(Map.of("amount", 10)).action());
    }
}
