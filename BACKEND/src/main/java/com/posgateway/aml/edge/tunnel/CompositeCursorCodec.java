package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cursors of §7.5 and §7.13.
 *
 * <ul>
 *   <li><b>Site cursor</b> ({@code tunnel/cursor.schema.json}): base64url (no padding) of
 *       {@code {v:1, k:[evaluatedAt, edgeAssessmentId], q}} — a documented keyset, so the cloud can
 *       mint one from the last item it consumed from a site.</li>
 *   <li><b>Composite cursor</b> ({@code tunnel/composite-cursor.schema.json}): what the Console gets:
 *       {@code <b64url json>.<b64url HMAC-SHA256(json)>}, the JSON mapping every site of the PSP to
 *       its site cursor, {@code START} or {@code DONE}. A bad MAC, an undecodable token, a schema
 *       failure or a {@code q} that does not match the request's params is {@code INVALID_CURSOR}.</li>
 * </ul>
 */
public class CompositeCursorCodec {

    /** Site state: nothing consumed from the site yet. */
    public static final String START = "START";
    /** Site state: the site is exhausted. */
    public static final String DONE = "DONE";

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final ObjectMapper mapper;
    private final TunnelSchemas schemas;

    /** Creates the codec with the shared Control Plane HMAC secret. */
    public CompositeCursorCodec(byte[] secret, ObjectMapper mapper, TunnelSchemas schemas) {
        if (secret == null || secret.length < 16) {
            throw new IllegalArgumentException("composite cursor secret must be at least 16 bytes");
        }
        this.secret = secret.clone();
        this.mapper = mapper;
        this.schemas = schemas;
    }

    /** {@code q}: SHA-256 hex of the JCS canonical JSON of the op params (§7.5). */
    public static String queryHash(JsonNode params) {
        return Jcs.sha256Hex(params);
    }

    /** Mints the site cursor for the last item consumed from a site. */
    public String mintSiteCursor(String q, String evaluatedAt, String edgeAssessmentId) {
        ObjectNode c = mapper.createObjectNode();
        c.put("v", 1);
        c.putArray("k").add(evaluatedAt).add(edgeAssessmentId);
        c.put("q", q);
        return B64.encodeToString(c.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Signs a composite cursor over the per-site states (sites in sorted order). */
    public String encode(String q, Map<String, String> sites) {
        ObjectNode c = mapper.createObjectNode();
        c.put("v", 1);
        c.put("q", q);
        ObjectNode s = c.putObject("sites");
        new TreeMap<>(sites).forEach(s::put);
        byte[] json = c.toString().getBytes(StandardCharsets.UTF_8);
        return B64.encodeToString(json) + "." + B64.encodeToString(hmac(json));
    }

    /**
     * Verifies and decodes a composite cursor for a request whose params hash to {@code q}.
     *
     * @return site id → state ({@code START}, {@code DONE} or a site cursor)
     * @throws EdgeDataException {@code INVALID_CURSOR} on any defect
     */
    public Map<String, String> decode(String token, String q) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1 || token.indexOf('.', dot + 1) >= 0) {
            throw invalid();
        }
        byte[] json;
        byte[] mac;
        try {
            json = B64D.decode(token.substring(0, dot));
            mac = B64D.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(mac, hmac(json))) {
            throw invalid();
        }
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (java.io.IOException e) {
            throw invalid();
        }
        if (!schemas.isValid("tunnel/composite-cursor.schema.json", node) || !q.equals(node.get("q").asText())) {
            throw invalid();
        }
        Map<String, String> out = new TreeMap<>();
        node.get("sites").fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        return out;
    }

    /** HMAC-SHA256 under the shared secret. */
    private byte[] hmac(byte[] data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return m.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** The INVALID_CURSOR failure. */
    private static EdgeDataException invalid() {
        return new EdgeDataException(TunnelError.of(TunnelErrorCode.INVALID_CURSOR, "cursor is invalid"));
    }
}
