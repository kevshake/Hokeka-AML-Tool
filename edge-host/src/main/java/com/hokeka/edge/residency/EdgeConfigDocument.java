package com.hokeka.edge.residency;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An accepted Edge config document (EDGE_DATA_RESIDENCY_CONTRACT.md §6), parsed into the values the
 * Edge components consume. Built only from a document that has already passed
 * {@code edge-config.schema.json}, so every required member is present and well-formed.
 *
 * @param configVersion   strictly increasing per PSP
 * @param pspId           the PSP the document belongs to
 * @param issuedAt        when the Control Plane issued it
 * @param retentionYears  {@code retention.years} as sent (the schema bounds it to 5..25; the purge
 *                        still clamps it in code, §6.3)
 * @param supportAccess   {@code supportAccess.enabled} (§9)
 * @param crossPspOptIn   {@code crossPspIntelligence.optIn} (§10)
 * @param ctr             CTR settings used by {@code filing.extract} (§7.6)
 * @param limitCount      number of {@code limits[]} entries
 * @param blocklistVersion {@code merchantBlocklist.version}
 * @param blockedMerchants merchant ids on the blocklist
 * @param sanctionsListVersion {@code sanctions.listVersion}
 * @param tunnel          tunnel settings (§6.1, §7.6.1)
 * @param document        the document as received (for history and status)
 * @param sha256          SHA-256 hex of its RFC 8785 canonical JSON
 */
public record EdgeConfigDocument(long configVersion,
                                 long pspId,
                                 Instant issuedAt,
                                 int retentionYears,
                                 boolean supportAccess,
                                 boolean crossPspOptIn,
                                 Ctr ctr,
                                 int limitCount,
                                 long blocklistVersion,
                                 Set<String> blockedMerchants,
                                 String sanctionsListVersion,
                                 Tunnel tunnel,
                                 JsonNode document,
                                 String sha256) {

    /**
     * CTR section (§6.1).
     *
     * @param enabled           whether the PSP files CTRs
     * @param timezone          zone that cuts business days
     * @param reportingCurrency currency CTR totals are reported in
     * @param thresholds        threshold in minor units per currency
     * @param cashChannels      channel values that count as cash
     * @param fxAsOf            when the FX rates were taken
     * @param fxBase            the rates' base currency
     * @param rates             units of {@code fxBase} per one unit of the keyed currency
     */
    public record Ctr(boolean enabled, ZoneId timezone, String reportingCurrency, Map<String, Long> thresholds,
                      Set<String> cashChannels, Instant fxAsOf, String fxBase, Map<String, BigDecimal> rates) {
    }

    /**
     * Tunnel section (§6.1).
     *
     * @param enabled               tunnel allowed by the Control Plane for this PSP
     * @param opsAllowed            ops the PSP allows
     * @param backfillAllowed       whether {@code ledger.backfill} may run (§11)
     * @param maxInflightPerSession in-flight cap per session (1..16)
     */
    public record Tunnel(boolean enabled, Set<String> opsAllowed, boolean backfillAllowed, int maxInflightPerSession) {
    }

    /**
     * Parses a schema-valid document.
     *
     * @param doc    the document (already validated against {@code edge-config.schema.json})
     * @param sha256 its canonical digest
     */
    public static EdgeConfigDocument parse(JsonNode doc, String sha256) {
        JsonNode ctrNode = doc.get("ctr");
        Map<String, Long> thresholds = new LinkedHashMap<>();
        for (JsonNode t : ctrNode.get("thresholds")) {
            thresholds.put(t.get("currency").asText(), t.get("amountMinor").asLong());
        }
        Set<String> cash = new LinkedHashSet<>();
        ctrNode.get("cashChannels").forEach(c -> cash.add(c.asText()));
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        JsonNode fx = ctrNode.get("fx");
        fx.get("rates").fields().forEachRemaining(e -> rates.put(e.getKey(), new BigDecimal(e.getValue().asText())));
        Ctr ctr = new Ctr(ctrNode.get("enabled").asBoolean(), ZoneId.of(ctrNode.get("businessDayTimezone").asText()),
                ctrNode.get("reportingCurrency").asText(), Collections.unmodifiableMap(thresholds),
                Collections.unmodifiableSet(cash), Instant.parse(fx.get("asOf").asText()), fx.get("base").asText(),
                Collections.unmodifiableMap(rates));

        JsonNode tunnelNode = doc.get("tunnel");
        Set<String> ops = new LinkedHashSet<>();
        tunnelNode.get("opsAllowed").forEach(o -> ops.add(o.asText()));
        Tunnel tunnel = new Tunnel(tunnelNode.get("enabled").asBoolean(), Collections.unmodifiableSet(ops),
                tunnelNode.get("backfillAllowed").asBoolean(), tunnelNode.get("maxInflightPerSession").asInt());

        Set<String> blocked = new LinkedHashSet<>();
        JsonNode blocklist = doc.get("merchantBlocklist");
        blocklist.get("entries").forEach(e -> blocked.add(e.get("merchantId").asText()));

        return new EdgeConfigDocument(doc.get("configVersion").asLong(), doc.get("pspId").asLong(),
                Instant.parse(doc.get("issuedAt").asText()), doc.get("retention").get("years").asInt(),
                doc.get("supportAccess").get("enabled").asBoolean(),
                doc.get("crossPspIntelligence").get("optIn").asBoolean(), ctr, doc.get("limits").size(),
                blocklist.get("version").asLong(), Collections.unmodifiableSet(blocked),
                doc.get("sanctions").get("listVersion").asText(), tunnel, doc.deepCopy(), sha256);
    }

    /** Whether {@code merchantId} is on the PSP's merchant blocklist. */
    public boolean merchantBlocked(String merchantId) {
        return merchantId != null && blockedMerchants.contains(merchantId);
    }

    /** {@code sanctions.listHash}: the list snapshot to screen with (contract 6.4). */
    public String sanctionsListHash() {
        return document.path("sanctions").path("listHash").asText("");
    }

    /** True when the Control Plane has published a sanctions list ({@code listVersion} is not "none"). */
    public boolean sanctionsListPublished() {
        return !"none".equals(sanctionsListVersion);
    }

    /** The ops the config allows, as an immutable list (for status output). */
    public List<String> opsAllowedList() {
        return List.copyOf(tunnel.opsAllowed());
    }
}
