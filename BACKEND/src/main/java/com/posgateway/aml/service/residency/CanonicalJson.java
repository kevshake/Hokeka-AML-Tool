package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

/**
 * Canonical JSON for hashing: object members sorted by name at every depth, array order kept, no
 * whitespace. Two documents with the same content always produce the same bytes, so the hash can
 * serve as a content identity (Edge config versioning, decision record duplicate detection).
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalJson() {
    }

    /** Returns a deep copy of {@code node} with every object's members in name order. */
    public static JsonNode canonicalize(JsonNode node) {
        if (node == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                names.add(it.next());
            }
            Collections.sort(names);
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            for (String name : names) {
                sorted.set(name, canonicalize(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                copy.add(canonicalize(item));
            }
            return copy;
        }
        return node;
    }

    /** Canonical UTF-8 bytes of {@code node}. */
    public static byte[] bytes(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(canonicalize(node)).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialise canonical JSON", e);
        }
    }

    /** Lowercase hex SHA-256 of the canonical bytes of {@code node}. */
    public static String sha256Hex(JsonNode node) {
        return sha256Hex(bytes(node));
    }

    /** Lowercase hex SHA-256 of {@code data}. */
    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
