package com.hokeka.edge.typology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.residency.EdgeConfigDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Edge Config documents for the typology tests, built from the shared contract fixture. */
final class TypologyFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TypologyFixtures() {
    }

    /**
     * The contract fixture {@code edge-config--extended-retention.json} (CTR on, USD 15 000.00
     * threshold, cash channels ATM / CASH_IN / AGENT, FX KES 0.0077 and EUR 1.0852 per USD) with no
     * limits, after {@code edit}.
     */
    static EdgeConfigDocument config(Consumer<ObjectNode> edit) {
        try {
            Path dir = Path.of(System.getProperty("contracts.dir", "../docs/contracts"), "tunnel", "examples");
            ObjectNode doc = (ObjectNode) MAPPER.readTree(Files.readAllBytes(dir.resolve("edge-config--extended-retention.json")));
            doc.putArray("limits");
            edit.accept(doc);
            return EdgeConfigDocument.parse(doc, "test");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Appends a limit to a document under edit. */
    static void limit(ObjectNode doc, long id, String scope, String metric, String window, String currency, long max,
                      String action) {
        ArrayNode limits = (ArrayNode) doc.get("limits");
        ObjectNode l = limits.addObject();
        l.put("limitId", id);
        l.put("scope", scope);
        l.put("metric", metric);
        l.put("window", window);
        if (currency != null) {
            l.put("currency", currency);
        }
        l.put("max", max);
        l.put("action", action);
    }
}
