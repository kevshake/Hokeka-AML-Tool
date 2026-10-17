package com.hokeka.edge.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.contract.Jcs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Verification and parsing rules of the sanctions/PEP list document (contract section 6.4), shared by
 * the fetcher and the ledger restore so a list is checked the same way whether it came from the
 * Control Plane a second ago or from the site ledger after a restart.
 */
public final class SanctionsListCodec {

    /** Manifest {@code format} this Edge understands. */
    public static final String FORMAT = "hokeka.sanctions.jsonl.gzip.v1";
    /** Most uncompressed bytes accepted per chunk (5 000 entries are ≈0.5 MB; 10 000 at the extreme ≈10 MB). */
    static final int MAX_CHUNK_UNCOMPRESSED = 64 * 1024 * 1024;

    private static final Pattern ENTRY_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}:[A-Za-z0-9][A-Za-z0-9._/-]{0,95}$");
    private static final Set<String> TYPES = Set.of("PERSON", "ORGANIZATION", "VESSEL", "UNKNOWN");
    private static final Set<String> ENTRY_MEMBERS = Set.of("id", "name", "aliases", "type", "pepLevel");

    private SanctionsListCodec() {
    }

    /** A list document that fails verification; the message is log-safe (never list content). */
    public static final class InvalidListException extends Exception {
        /** @param message log-safe reason */
        public InvalidListException(String message) {
            super(message);
        }
    }

    /**
     * {@code listHash}: SHA-256 hex of one {@code <index>:<sha256>:<entryCount>\n} line per manifest
     * chunk, in array order (which must be index order).
     */
    public static String listHash(JsonNode chunks) throws InvalidListException {
        StringBuilder text = new StringBuilder();
        int expected = 0;
        for (JsonNode chunk : chunks) {
            if (chunk.path("index").asInt(-1) != expected) {
                throw new InvalidListException("manifest chunks are not in index order");
            }
            text.append(expected).append(':').append(chunk.path("sha256").asText()).append(':')
                    .append(chunk.path("entryCount").asInt()).append('\n');
            expected++;
        }
        return Jcs.sha256Hex(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Checks a schema-valid manifest's internal consistency: format, listHash re-derived from the
     * chunk lines, entryCount = sum of chunk counts.
     */
    public static void verifyManifest(JsonNode manifest) throws InvalidListException {
        if (!FORMAT.equals(manifest.path("format").asText())) {
            throw new InvalidListException("unsupported list format");
        }
        String derived = listHash(manifest.get("chunks"));
        if (!derived.equals(manifest.path("listHash").asText())) {
            throw new InvalidListException("listHash does not match the chunk list");
        }
        long sum = 0;
        for (JsonNode chunk : manifest.get("chunks")) {
            sum += chunk.path("entryCount").asLong();
        }
        if (sum != manifest.path("entryCount").asLong()) {
            throw new InvalidListException("entryCount does not match the chunks");
        }
    }

    /** gunzip with a size cap. */
    public static byte[] gunzip(byte[] gzip) throws InvalidListException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            byte[] out = in.readNBytes(MAX_CHUNK_UNCOMPRESSED + 1);
            if (out.length > MAX_CHUNK_UNCOMPRESSED) {
                throw new InvalidListException("chunk exceeds " + MAX_CHUNK_UNCOMPRESSED + " bytes uncompressed");
            }
            return out;
        } catch (IOException e) {
            throw new InvalidListException("chunk is not valid gzip");
        }
    }

    /**
     * Verifies an uncompressed chunk against its manifest line and parses its entries
     * ({@code sanctions-list-entry.schema.json}, checked in code because a list has up to millions).
     *
     * @param raw           uncompressed JSON lines
     * @param expectedSha   manifest {@code sha256}
     * @param expectedCount manifest {@code entryCount}
     */
    public static List<SanctionsEntry> parseChunk(byte[] raw, String expectedSha, int expectedCount,
                                                  ObjectMapper mapper) throws InvalidListException {
        if (!Jcs.sha256Hex(raw).equals(expectedSha)) {
            throw new InvalidListException("chunk sha256 does not match the manifest");
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        List<SanctionsEntry> out = new ArrayList<>(expectedCount);
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                throw new InvalidListException("chunk does not end with a newline");
            }
            out.add(entry(text.substring(start, end), mapper));
            start = end + 1;
        }
        if (out.size() != expectedCount) {
            throw new InvalidListException("chunk holds " + out.size() + " entries, manifest says " + expectedCount);
        }
        return out;
    }

    /** One JSON line → entry, enforcing the entry schema. */
    public static SanctionsEntry entry(String line, ObjectMapper mapper) throws InvalidListException {
        JsonNode node;
        try {
            node = mapper.readTree(line);
        } catch (IOException e) {
            throw new InvalidListException("chunk line is not JSON");
        }
        if (node == null || !node.isObject()) {
            throw new InvalidListException("chunk line is not an object");
        }
        var names = node.fieldNames();
        while (names.hasNext()) {
            if (!ENTRY_MEMBERS.contains(names.next())) {
                throw new InvalidListException("entry has an unknown member");
            }
        }
        JsonNode id = node.get("id");
        JsonNode name = node.get("name");
        JsonNode aliases = node.get("aliases");
        JsonNode type = node.get("type");
        if (id == null || !id.isTextual() || !ENTRY_ID.matcher(id.asText()).matches()) {
            throw new InvalidListException("entry id is malformed");
        }
        if (name == null || !name.isTextual() || name.asText().length() > 512) {
            throw new InvalidListException("entry name is malformed");
        }
        if (aliases == null || !aliases.isArray() || aliases.size() > 512) {
            throw new InvalidListException("entry aliases are malformed");
        }
        List<String> list = new ArrayList<>(aliases.size());
        for (JsonNode alias : aliases) {
            if (!alias.isTextual() || alias.asText().isEmpty() || alias.asText().length() > 512) {
                throw new InvalidListException("entry alias is malformed");
            }
            list.add(alias.asText());
        }
        if (type == null || !type.isTextual() || !TYPES.contains(type.asText())) {
            throw new InvalidListException("entry type is malformed");
        }
        String pep = null;
        if (node.has("pepLevel")) {
            pep = node.get("pepLevel").asText();
            if (!"PEP".equals(pep) && !"RCA".equals(pep)) {
                throw new InvalidListException("entry pepLevel is malformed");
            }
        }
        return new SanctionsEntry(id.asText(), name.asText(), list, type.asText(), pep);
    }
}
