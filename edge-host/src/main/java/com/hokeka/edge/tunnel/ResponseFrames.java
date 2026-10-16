package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a page of items into {@code response}/{@code chunk} bodies that each fit one 1 MiB frame
 * (EDGE_DATA_RESIDENCY_CONTRACT.md §7.7: "the Edge MUST split a page across several response frames
 * when it would not fit 1 MiB"). A sealed frame is 4/3 of its plaintext (base64) plus about 300
 * bytes of framing, so a body whose items stay under {@link #BODY_BUDGET_BYTES} (plus at most 200
 * missing ids and a 2 KiB cursor) seals to well under 1 MiB. Items are grouped greedily; only a
 * single item larger than the budget can still overflow, and sealing then reports
 * {@code FRAME_TOO_LARGE}.
 */
public final class ResponseFrames {

    /** Plaintext budget for the items of one frame: (1 MiB − framing) × 3/4, minus a safety margin. */
    static final int BODY_BUDGET_BYTES = 700_000;

    private final ObjectMapper mapper;

    /** @param mapper JSON mapper */
    public ResponseFrames(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Groups items so each group's JSON fits the budget (at least one group, possibly empty). */
    public List<List<ObjectNode>> groups(List<ObjectNode> items, int maxItemsPerFrame) {
        List<List<ObjectNode>> out = new ArrayList<>();
        List<ObjectNode> current = new ArrayList<>();
        long bytes = 0;
        for (ObjectNode item : items) {
            long size = size(item);
            if (!current.isEmpty() && (bytes + size > BODY_BUDGET_BYTES || current.size() >= maxItemsPerFrame)) {
                out.add(current);
                current = new ArrayList<>();
                bytes = 0;
            }
            current.add(item);
            bytes += size + 1;
        }
        out.add(current);
        return out;
    }

    /**
     * One {@code tunnel/response.schema.json} body.
     *
     * @param op               op name (or {@code hold.ack})
     * @param seq              frame sequence number within the request
     * @param last             whether this frame ends the answer
     * @param items            the frame's items
     * @param nextCursor       next-page cursor (last frame only), or null
     * @param missing          absent ids (last frame only), or null/empty
     * @param incompleteReason SCAN_LIMIT / DEPTH_LIMIT / DEADLINE (last frame only), or null
     * @param totalRows        export total (last frame only), or null
     */
    public ObjectNode body(String op, int seq, boolean last, List<ObjectNode> items, String nextCursor,
                           List<String> missing, String incompleteReason, Long totalRows) {
        ObjectNode b = mapper.createObjectNode();
        b.put("op", op);
        b.put("status", "OK");
        b.put("seq", seq);
        b.put("last", last);
        ArrayNode arr = b.putArray("items");
        items.forEach(arr::add);
        if (last) {
            if (nextCursor != null) {
                b.put("nextCursor", nextCursor);
            }
            if (missing != null && !missing.isEmpty()) {
                ArrayNode m = b.putArray("missing");
                missing.forEach(m::add);
            }
            if (incompleteReason != null) {
                b.put("incomplete", true);
                b.put("incompleteReason", incompleteReason);
            }
            if (totalRows != null) {
                b.put("totalRows", totalRows);
            }
        }
        return b;
    }

    /** Serialised size of one item in bytes. */
    private long size(ObjectNode item) {
        try {
            return mapper.writeValueAsBytes(item).length;
        } catch (JsonProcessingException e) {
            return BODY_BUDGET_BYTES;
        }
    }
}
