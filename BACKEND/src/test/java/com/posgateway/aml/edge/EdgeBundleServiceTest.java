package com.posgateway.aml.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the control plane compiles rules into the edge Rule IR, seals it with HSE-1, and that it
 * round-trips — and emits the cross-language interop vector the Rust edge test opens.
 */
class EdgeBundleServiceTest {

    private final EdgeBundleService svc = new EdgeBundleService();
    private final HokekaSecureEnvelope hse = new HokekaSecureEnvelope();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compilesSealsAndRoundTripsTheRuleIrBundle() throws Exception {
        KeyPair cpSigner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair edge = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        byte[] edgePubRaw = HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edge.getPublic());
        String context = "psp-42";

        // A VPN-hold rule — identical shape to the Rust rule-core deserialize test.
        ObjectNode condition = svc.cmp("ip_vpn_or_proxy", "EQ", true);
        EdgeBundleService.CompiledRule rule = new EdgeBundleService.CompiledRule(
                100L, "VPN hold", null, condition, "HOLD", 25, 5);

        byte[] sealed = svc.compileAndSeal(37L, 42L, List.of(rule), edgePubRaw, cpSigner.getPrivate(), context);

        // Edge opens it and gets the IR bundle.
        byte[] plaintext = hse.open(sealed, edge.getPrivate(), cpSigner.getPublic(),
                context.getBytes(StandardCharsets.UTF_8));
        JsonNode bundle = mapper.readTree(plaintext);

        assertEquals(37, bundle.get("version").asInt());
        assertEquals(42, bundle.get("psp_id").asInt());
        JsonNode r0 = bundle.get("rules").get(0);
        assertEquals(100, r0.get("id").asInt());
        assertEquals("HOLD", r0.get("action").asText());
        assertEquals(25, r0.get("score").asInt());
        assertEquals("cmp", r0.get("condition").get("type").asText());
        assertEquals("ip_vpn_or_proxy", r0.get("condition").get("field").asText());
        assertEquals("EQ", r0.get("condition").get("op").asText());
        assertTrue(r0.get("condition").get("value").asBoolean());

        // Emit the interop vector for the Rust `hse-crypto` test (best-effort; never fails the test).
        writeInteropVector(sealed, edge, cpSigner, context, plaintext);
    }

    private void writeInteropVector(byte[] sealed, KeyPair edge, KeyPair cpSigner, String context,
                                    byte[] plaintext) {
        try {
            byte[] edgePrivScalar = ((XECPrivateKey) edge.getPrivate()).getScalar().orElseThrow();
            byte[] cpPubRaw = rawEd25519Public((EdECPublicKey) cpSigner.getPublic());
            ObjectNode v = mapper.createObjectNode();
            v.put("_note", "HSE-1 interop vector: Rust edge must open(envelope, edge_x25519_priv, cp_ed25519_pub, context) == plaintext");
            v.put("edge_x25519_priv", hex(edgePrivScalar));
            v.put("cp_ed25519_pub", hex(cpPubRaw));
            v.put("context", hex(context.getBytes(StandardCharsets.UTF_8)));
            v.put("envelope", hex(sealed));
            v.put("plaintext", hex(plaintext));
            Path dir = Path.of("..", "edge-engine", "test-vectors");
            Files.createDirectories(dir);
            Files.write(dir.resolve("hse1_vector.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(v));
        } catch (Exception ignored) {
            // Vector emission is a convenience; the round-trip assertions above are the real proof.
        }
    }

    /** RFC-8032 compressed Ed25519 public key: 32-byte little-endian y with x-parity in the top bit. */
    private static byte[] rawEd25519Public(EdECPublicKey pub) {
        var point = pub.getPoint();
        byte[] be = point.getY().toByteArray();
        byte[] be32 = new byte[32];
        int copy = Math.min(be.length, 32);
        System.arraycopy(be, be.length - copy, be32, 32 - copy, copy);
        byte[] le = new byte[32];
        for (int i = 0; i < 32; i++) {
            le[i] = be32[31 - i];
        }
        if (point.isXOdd()) {
            le[31] |= (byte) 0x80;
        }
        return le;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }
}
