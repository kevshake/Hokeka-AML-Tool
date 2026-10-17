package com.hokeka.edge.sanctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.Jcs;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Builds sanctions list documents exactly as the Control Plane publishes them (BACKEND
 * {@code EdgeSanctionsSnapshotService} / {@code EdgeSanctionsListService}, contract 6.4): JSON-lines
 * chunks, gzip, uncompressed SHA-256, listHash over the chunk lines, manifest and chunk documents.
 * An independent implementation, so the Edge side is tested against the contract, not itself.
 */
public final class SanctionsTestLists {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A built list. */
    public static final class Built {
        public final ObjectNode manifest;
        public final List<ObjectNode> chunks;
        public final List<byte[]> gzips;
        public final String listHash;

        Built(ObjectNode manifest, List<ObjectNode> chunks, List<byte[]> gzips, String listHash) {
            this.manifest = manifest;
            this.chunks = chunks;
            this.gzips = gzips;
            this.listHash = listHash;
        }
    }

    private SanctionsTestLists() {
    }

    /** One entry object. */
    public static ObjectNode entry(String id, String name, List<String> aliases, String type, String pepLevel) {
        ObjectNode e = MAPPER.createObjectNode();
        e.put("id", id);
        e.put("name", name);
        ArrayNode a = e.putArray("aliases");
        aliases.forEach(a::add);
        e.put("type", type);
        if (pepLevel != null) {
            e.put("pepLevel", pepLevel);
        }
        return e;
    }

    /** Builds the manifest and chunk documents for {@code entries}, {@code perChunk} entries per chunk. */
    public static Built build(List<ObjectNode> entries, int perChunk, String listVersion, String publishedAt)
            throws Exception {
        List<byte[]> raws = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int start = 0; start < entries.size(); start += perChunk) {
            StringBuilder lines = new StringBuilder();
            List<ObjectNode> slice = entries.subList(start, Math.min(entries.size(), start + perChunk));
            for (ObjectNode e : slice) {
                lines.append(MAPPER.writeValueAsString(e)).append('\n');
            }
            raws.add(lines.toString().getBytes(StandardCharsets.UTF_8));
            counts.add(slice.size());
        }
        StringBuilder hashText = new StringBuilder();
        for (int i = 0; i < raws.size(); i++) {
            hashText.append(i).append(':').append(Jcs.sha256Hex(raws.get(i))).append(':').append(counts.get(i)).append('\n');
        }
        String listHash = Jcs.sha256Hex(hashText.toString().getBytes(StandardCharsets.UTF_8));
        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("v", 1);
        manifest.put("listVersion", listVersion);
        manifest.put("listHash", listHash);
        manifest.put("publishedAt", publishedAt);
        manifest.put("entryCount", entries.size());
        manifest.put("format", "hokeka.sanctions.jsonl.gzip.v1");
        ArrayNode items = manifest.putArray("chunks");
        List<ObjectNode> chunks = new ArrayList<>();
        List<byte[]> gzips = new ArrayList<>();
        for (int i = 0; i < raws.size(); i++) {
            byte[] gz = gzip(raws.get(i));
            gzips.add(gz);
            ObjectNode item = items.addObject();
            item.put("index", i);
            item.put("sha256", Jcs.sha256Hex(raws.get(i)));
            item.put("entryCount", counts.get(i));
            item.put("bytes", gz.length);
            ObjectNode chunk = MAPPER.createObjectNode();
            chunk.put("v", 1);
            chunk.put("listHash", listHash);
            chunk.put("index", i);
            chunk.put("sha256", Jcs.sha256Hex(raws.get(i)));
            chunk.put("entryCount", counts.get(i));
            chunk.put("encoding", "gzip+base64");
            chunk.put("data", Base64.getEncoder().encodeToString(gz));
            chunks.add(chunk);
        }
        return new Built(manifest, chunks, gzips, listHash);
    }

    /** gzip. */
    static byte[] gzip(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }
}
