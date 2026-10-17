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
 * @param limits          {@code limits[]}, enforced on evaluate by {@code ComplianceOverlay}
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
                                 List<Limit> limits,
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
     * @param structuringFloorRatio  share of the threshold where the structuring band starts
     *                               ({@code ctr.structuring.floorRatio}, default 0.80)
     * @param structuringMinTransactions band transactions in 24 h that make structuring (default 2)
     */
    public record Ctr(boolean enabled, ZoneId timezone, String reportingCurrency, Map<String, Long> thresholds,
                      Set<String> cashChannels, Instant fxAsOf, String fxBase, Map<String, BigDecimal> rates,
                      BigDecimal structuringFloorRatio, int structuringMinTransactions) {

        /** The cloud detector's defaults, used when the document has no {@code structuring} section. */
        public static final BigDecimal DEFAULT_FLOOR_RATIO = new BigDecimal("0.80");
        /** Default repeat count. */
        public static final int DEFAULT_MIN_TRANSACTIONS = 2;
    }

    /**
     * One {@code limits[]} entry (§6.1).
     *
     * @param limitId    id
     * @param scope      CARD / MERCHANT / PSP
     * @param metric     COUNT / AMOUNT
     * @param window     PT1H / P1D / P7D / P30D
     * @param currency   currency of {@code max} for AMOUNT, else null
     * @param max        the maximum (count, or minor units of {@code currency})
     * @param action     ALERT / HOLD / BLOCK
     * @param merchantId optional filter: only this merchant's transactions
     * @param mcc        optional filter: only this MCC
     */
    public record Limit(long limitId, String scope, String metric, String window, String currency, long max,
                        String action, String merchantId, String mcc) {
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
        JsonNode structuring = ctrNode.get("structuring");
        BigDecimal floor = structuring == null ? Ctr.DEFAULT_FLOOR_RATIO
                : new BigDecimal(structuring.get("floorRatio").asText());
        int minTxns = structuring == null ? Ctr.DEFAULT_MIN_TRANSACTIONS : structuring.get("minTransactions").asInt();
        Ctr ctr = new Ctr(ctrNode.get("enabled").asBoolean(), ZoneId.of(ctrNode.get("businessDayTimezone").asText()),
                ctrNode.get("reportingCurrency").asText(), Collections.unmodifiableMap(thresholds),
                Collections.unmodifiableSet(cash), Instant.parse(fx.get("asOf").asText()), fx.get("base").asText(),
                Collections.unmodifiableMap(rates), floor, minTxns);

        List<Limit> limits = new java.util.ArrayList<>();
        for (JsonNode l : doc.get("limits")) {
            limits.add(new Limit(l.get("limitId").asLong(), l.get("scope").asText(), l.get("metric").asText(),
                    l.get("window").asText(), l.hasNonNull("currency") ? l.get("currency").asText() : null,
                    l.get("max").asLong(), l.get("action").asText(),
                    l.hasNonNull("merchantId") ? l.get("merchantId").asText() : null,
                    l.hasNonNull("mcc") ? l.get("mcc").asText() : null));
        }

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
                doc.get("crossPspIntelligence").get("optIn").asBoolean(), ctr, List.copyOf(limits),
                blocklist.get("version").asLong(), Collections.unmodifiableSet(blocked),
                doc.get("sanctions").get("listVersion").asText(), tunnel, doc.deepCopy(), sha256);
    }

    /** Number of {@code limits[]} entries (status output). */
    public int limitCount() {
        return limits.size();
    }

    /** Whether {@code merchantId} is on the PSP's merchant blocklist. */
    public boolean merchantBlocked(String merchantId) {
        return merchantId != null && blockedMerchants.contains(merchantId);
    }

    /** The ops the config allows, as an immutable list (for status output). */
    public List<String> opsAllowedList() {
        return List.copyOf(tunnel.opsAllowed());
    }
}
