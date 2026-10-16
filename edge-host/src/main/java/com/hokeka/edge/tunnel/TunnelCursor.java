package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.contract.Jcs;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The site cursor of list ops (EDGE_DATA_RESIDENCY_CONTRACT.md §7.5, {@code tunnel/cursor.schema.json}):
 * base64url without padding of {@code {v:1, k:[evaluatedAt, edgeAssessmentId], q}}, where {@code k}
 * is the key of the last item returned and {@code q} the SHA-256 hex of the RFC 8785 canonical JSON
 * of the request's params. It is a documented keyset, not an opaque token: the Control Plane mints
 * the same form when it merges sites (§7.13).
 */
public final class TunnelCursor {

    private static final String SCHEMA = "tunnel/cursor.schema.json";

    /**
     * A decoded keyset position: items strictly after it in (evaluatedAt desc, id desc) order.
     *
     * @param evaluatedAt evaluatedAt of the last item returned
     * @param id          its edgeAssessmentId
     */
    public record Key(Instant evaluatedAt, UUID id) {
    }

    private final ObjectMapper mapper;
    private final ContractSchemas schemas;

    /** @param mapper JSON mapper; @param schemas contract schemas (the cursor schema) */
    public TunnelCursor(ObjectMapper mapper, ContractSchemas schemas) {
        this.mapper = mapper;
        this.schemas = schemas;
    }

    /** {@code q}: SHA-256 hex of the JCS canonical JSON of the op params. */
    public static String queryHash(JsonNode params) {
        return Jcs.sha256Hex(params);
    }

    /** Mints the cursor positioned after the item with key ({@code evaluatedAt}, {@code id}). */
    public String encode(String q, Instant evaluatedAt, UUID id) {
        ObjectNode c = mapper.createObjectNode();
        c.put("v", 1);
        c.putArray("k").add(TunnelTime.millis(evaluatedAt)).add(id.toString());
        c.put("q", q);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(c.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a cursor for the request with params hash {@code q}.
     *
     * @throws TunnelException {@code INVALID_CURSOR} when it does not decode, fails the cursor schema
     *                         or was minted for different params
     */
    public Key decode(String cursor, String q) throws TunnelException {
        JsonNode node;
        try {
            node = mapper.readTree(Base64.getUrlDecoder().decode(cursor));
        } catch (IllegalArgumentException | java.io.IOException e) {
            throw new TunnelException(TunnelErrorCode.INVALID_CURSOR, "cursor does not decode", "/page/cursor", null);
        }
        if (node == null || !schemas.violations(SCHEMA, node).isEmpty()) {
            throw new TunnelException(TunnelErrorCode.INVALID_CURSOR, "cursor fails cursor.schema.json", "/page/cursor",
                    null);
        }
        if (!node.get("q").asText().equals(q)) {
            throw new TunnelException(TunnelErrorCode.INVALID_CURSOR, "cursor was minted for different params",
                    "/page/cursor", null);
        }
        try {
            return new Key(Instant.parse(node.get("k").get(0).asText()), UUID.fromString(node.get("k").get(1).asText()));
        } catch (RuntimeException e) {
            throw new TunnelException(TunnelErrorCode.INVALID_CURSOR, "cursor key is malformed", "/page/cursor", null);
        }
    }
}
