package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.billing.CurrencyRate;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.EdgeLimit;
import com.posgateway.aml.entity.residency.EdgeMerchantBlocklistEntry;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.repository.CurrencyRateRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.residency.EdgeLimitRepository;
import com.posgateway.aml.repository.residency.EdgeMerchantBlocklistRepository;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Assembles the content sections of a PSP's Edge config document
 * ({@code docs/contracts/edge-config.schema.json}, contract section 6.1) from cloud state:
 *
 * <table>
 *   <caption>Sources</caption>
 *   <tr><th>Section</th><th>Source</th></tr>
 *   <tr><td>retention</td><td>{@code psps.retention_years} + approved pending reduction (maker-checker)</td></tr>
 *   <tr><td>supportAccess, crossPspIntelligence</td><td>{@code psps} consent columns</td></tr>
 *   <tr><td>ctr</td><td>{@code psp_edge_settings}; threshold defaults to {@code compliance.kenya.ctr.threshold-usd};
 *       FX from regulatory-approved {@code currency_rates} (same validity rules as cloud CTR evaluation)</td></tr>
 *   <tr><td>limits</td><td>{@code edge_limits}</td></tr>
 *   <tr><td>merchantBlocklist</td><td>{@code edge_merchant_blocklist}</td></tr>
 *   <tr><td>sanctions</td><td>newest READY Edge list snapshot ({@code edge_sanctions_snapshots}, R10b)</td></tr>
 *   <tr><td>tunnel</td><td>{@code psp_edge_settings}</td></tr>
 * </table>
 *
 * <p>The returned object holds everything except {@code configVersion} and {@code issuedAt}, so its
 * canonical hash identifies the content for versioning.
 */
@Component
public class EdgeConfigDocumentBuilder {

    /** Base currency of the FX table: {@code currency_rates.rate_to_usd} is "USD per unit". */
    static final String FX_BASE = "USD";

    private static final Pattern LIST_VERSION_ILLEGAL = Pattern.compile("[^A-Za-z0-9._-]");

    private final PspRepository psps;
    private final PspDataSettingsService dataSettings;
    private final PspEdgeSettingsRepository edgeSettings;
    private final EdgeLimitRepository limits;
    private final EdgeMerchantBlocklistRepository blocklist;
    private final CurrencyRateRepository currencyRates;
    private final EdgeSanctionsSnapshotStore sanctionsSnapshots;
    private final BigDecimal defaultCtrThresholdUsd;
    private final Duration maximumFxAge;

    public EdgeConfigDocumentBuilder(PspRepository psps,
                                     PspDataSettingsService dataSettings,
                                     PspEdgeSettingsRepository edgeSettings,
                                     EdgeLimitRepository limits,
                                     EdgeMerchantBlocklistRepository blocklist,
                                     CurrencyRateRepository currencyRates,
                                     EdgeSanctionsSnapshotStore sanctionsSnapshots,
                                     @Value("${compliance.kenya.ctr.threshold-usd:15000}") BigDecimal defaultCtrThresholdUsd,
                                     @Value("${compliance.regulatory-fx.max-age-hours:72}") long maximumFxAgeHours) {
        this.psps = psps;
        this.dataSettings = dataSettings;
        this.edgeSettings = edgeSettings;
        this.limits = limits;
        this.blocklist = blocklist;
        this.currencyRates = currencyRates;
        this.sanctionsSnapshots = sanctionsSnapshots;
        this.defaultCtrThresholdUsd = defaultCtrThresholdUsd;
        this.maximumFxAge = Duration.ofHours(Math.max(1L, maximumFxAgeHours));
    }

    /**
     * Builds the version-independent content of the PSP's config document at {@code now}.
     * Read-write because reading retention first applies any approved change that has come due.
     */
    @Transactional
    public ObjectNode buildContent(Long pspId, Instant now) {
        Psp psp = psps.findById(pspId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PSP not found"));
        PspEdgeSettings settings = edgeSettings.findById(pspId).orElseGet(() -> PspEdgeSettings.defaultsFor(pspId));

        ObjectNode doc = JsonNodeFactory.instance.objectNode();
        doc.put("v", 1);
        doc.put("pspId", pspId);
        doc.set("retention", retention(pspId));
        doc.set("supportAccess", supportAccess(psp));
        doc.set("crossPspIntelligence", crossPsp(psp));
        doc.set("ctr", ctr(settings, now));
        doc.set("limits", limits(pspId));
        doc.set("merchantBlocklist", merchantBlocklist(pspId));
        doc.set("sanctions", sanctions());
        doc.set("tunnel", tunnel(settings));
        return doc;
    }

    /** {@code retention}: years in force, since when, and an approved reduction not yet in force. */
    private ObjectNode retention(Long pspId) {
        PspDataSettingsService.RetentionState state = dataSettings.retentionState(pspId);
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("years", Math.max(PspDataSettingsService.MIN_RETENTION_YEARS, state.years()));
        node.put("effectiveSince", instant(state.effectiveSince()));
        if (state.pending() != null) {
            ObjectNode pending = node.putObject("pendingChange");
            pending.put("years", state.pending().years());
            pending.put("approvedAt", instant(state.pending().approvedAt()));
            pending.put("effectiveAt", instant(state.pending().effectiveAt()));
        }
        return node;
    }

    /** {@code supportAccess}: the PSP's consent and when it last changed. */
    private static ObjectNode supportAccess(Psp psp) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("enabled", Boolean.TRUE.equals(psp.getSupportAccessEnabled()));
        node.put("changedAt", instant(psp.getSupportAccessChangedAt()));
        return node;
    }

    /**
     * {@code crossPspIntelligence}: the opt-in and when it changed. {@code indicatorSetVersion} is
     * optional in the schema and is left out: no indicator set has been published yet (indicator sets
     * are built from filed SARs by work package R10b).
     */
    private static ObjectNode crossPsp(Psp psp) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("optIn", Boolean.TRUE.equals(psp.getCrossPspOptIn()));
        node.put("changedAt", instant(psp.getCrossPspOptInChangedAt()));
        return node;
    }

    /** {@code ctr}: CTR switch, business-day zone, threshold, cash channels and approved FX rates. */
    private ObjectNode ctr(PspEdgeSettings settings, Instant now) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        String reporting = settings.getCtrReportingCurrency() == null
                ? FX_BASE : settings.getCtrReportingCurrency().toUpperCase(Locale.ROOT);
        node.put("enabled", settings.isCtrEnabled());
        node.put("aggregation", "CARD_BUSINESS_DAY");
        node.put("businessDayTimezone", settings.getCtrBusinessDayTimezone());
        node.put("reportingCurrency", reporting);
        ArrayNode thresholds = node.putArray("thresholds");
        Long thresholdMinor = settings.getCtrThresholdMinor() != null
                ? settings.getCtrThresholdMinor()
                : (FX_BASE.equals(reporting) ? usdToMinor(defaultCtrThresholdUsd) : null);
        if (thresholdMinor != null) {
            ObjectNode threshold = thresholds.addObject();
            threshold.put("currency", reporting);
            threshold.put("amountMinor", thresholdMinor);
        }
        ArrayNode channels = node.putArray("cashChannels");
        settings.getCtrCashChannels().stream().distinct().sorted().forEach(channels::add);
        node.set("fx", fx(now));
        return node;
    }

    /**
     * {@code ctr.fx}: every active, regulatory-approved, effective, unexpired and not-stale rate,
     * exactly the rates {@code RegulatoryExchangeRateService} would accept for a cloud CTR check,
     * as decimal strings of USD per one unit. {@code asOf} is the newest included rate's effective
     * time (epoch when only USD parity is available).
     */
    private ObjectNode fx(Instant now) {
        ObjectNode fx = JsonNodeFactory.instance.objectNode();
        ObjectNode rates = JsonNodeFactory.instance.objectNode();
        rates.put(FX_BASE, "1");
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        Instant asOf = Instant.EPOCH;
        List<CurrencyRate> usable = currencyRates.findAll().stream()
                .filter(rate -> usableRate(rate, localNow))
                .sorted(Comparator.comparing(CurrencyRate::getCurrencyCode))
                .toList();
        for (CurrencyRate rate : usable) {
            String code = rate.getCurrencyCode().toUpperCase(Locale.ROOT);
            if (FX_BASE.equals(code)) {
                continue;
            }
            String decimal = decimalString(rate.getRateToUsd());
            if (decimal == null) {
                continue;
            }
            rates.put(code, decimal);
            Instant effective = rate.getEffectiveAt().atZone(ZoneId.systemDefault()).toInstant();
            if (effective.isAfter(asOf)) {
                asOf = effective;
            }
        }
        fx.put("asOf", instant(asOf));
        fx.put("base", FX_BASE);
        fx.set("rates", rates);
        return fx;
    }

    /** Same acceptance rules as RegulatoryExchangeRateService.convertToUsd. */
    private boolean usableRate(CurrencyRate rate, LocalDateTime now) {
        return rate.getCurrencyCode() != null
                && rate.getCurrencyCode().matches("[A-Za-z]{3}")
                && Boolean.TRUE.equals(rate.getIsActive())
                && rate.isRegulatoryApproved()
                && rate.getRateToUsd() != null && rate.getRateToUsd().signum() > 0
                && rate.getRateSource() != null && !rate.getRateSource().isBlank()
                && rate.getEffectiveAt() != null && !rate.getEffectiveAt().isAfter(now)
                && (rate.getExpiresAt() == null || !rate.getExpiresAt().isBefore(now))
                && !rate.getEffectiveAt().isBefore(now.minus(maximumFxAge));
    }

    /** {@code limits[]}: active Edge limits in limitId order. */
    private ArrayNode limits(Long pspId) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (EdgeLimit limit : limits.findByPspIdAndActiveTrueOrderByIdAsc(pspId)) {
            ObjectNode item = array.addObject();
            item.put("limitId", limit.getId());
            item.put("scope", limit.getScope());
            item.put("metric", limit.getMetric());
            item.put("window", limit.getWindowCode());
            if (limit.getCurrency() != null) {
                item.put("currency", limit.getCurrency());
            }
            item.put("max", limit.getMaxValue());
            item.put("action", limit.getAction());
            if (limit.getMerchantId() != null) {
                item.put("merchantId", limit.getMerchantId());
            }
            if (limit.getMcc() != null) {
                item.put("mcc", limit.getMcc());
            }
        }
        return array;
    }

    /** {@code merchantBlocklist}: version (inserts + removals) and the active entries. */
    private ObjectNode merchantBlocklist(Long pspId) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("version", blocklist.version(pspId));
        ArrayNode entries = node.putArray("entries");
        for (EdgeMerchantBlocklistEntry entry : blocklist.findByPspIdAndRemovedAtIsNullOrderByMerchantIdAsc(pspId)) {
            ObjectNode item = entries.addObject();
            item.put("merchantId", entry.getMerchantId());
            item.put("reasonCode", entry.getReasonCode());
            item.put("since", instant(entry.getSince()));
        }
        return node;
    }

    /**
     * {@code sanctions}: identity of the newest published Edge list snapshot (contract 6.4), the
     * document {@code GET /api/v1/edge/sanctions/list} serves. {@code listHash} is that snapshot's hash
     * over its chunk lines, so the Edge can tell which list to fetch and verify it got that one.
     * Before any snapshot is published the section says {@code listVersion "none"} with the hash of
     * the empty string and the epoch, which no real snapshot can match.
     */
    private ObjectNode sanctions() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        EdgeSanctionsSnapshotStore.Snapshot latest = sanctionsSnapshots.latestReady().orElse(null);
        if (latest == null) {
            node.put("listVersion", "none");
            node.put("listHash", CanonicalJson.sha256Hex(new byte[0]));
            node.put("publishedAt", instant(Instant.EPOCH));
            return node;
        }
        node.put("listVersion", listVersion(latest.listVersion(), latest.publishedAt()));
        node.put("listHash", latest.listHash());
        node.put("publishedAt", instant(latest.publishedAt()));
        return node;
    }

    /** {@code tunnel}: cloud-side tunnel switch, allowed ops, backfill flag, in-flight cap. */
    private static ObjectNode tunnel(PspEdgeSettings settings) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("enabled", settings.isTunnelEnabled());
        ArrayNode ops = node.putArray("opsAllowed");
        settings.getTunnelOpsAllowed().stream().distinct().forEach(ops::add);
        node.put("backfillAllowed", settings.isTunnelBackfillAllowed());
        node.put("maxInflightPerSession", Math.max(1, Math.min(16, settings.getTunnelMaxInflight())));
        return node;
    }

    /** Sanitises a downloaded list version into the schema's {@code ^[A-Za-z0-9._-]{1,64}$}. */
    static String listVersion(String raw, Instant published) {
        String base = raw == null || raw.isBlank() ? published.toString() : raw.trim();
        String cleaned = LIST_VERSION_ILLEGAL.matcher(base).replaceAll("-");
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        return cleaned.isEmpty() ? "none" : cleaned;
    }

    /** USD major units to minor units (cents), rounded half-up. */
    private static long usdToMinor(BigDecimal usd) {
        return usd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** A positive rate as a plain decimal string matching the schema's decimalString, or null. */
    static String decimalString(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return null;
        }
        BigDecimal scaled = value.setScale(Math.min(12, Math.max(0, value.scale())), RoundingMode.HALF_UP)
                .stripTrailingZeros();
        String plain = scaled.toPlainString();
        return plain.matches("^[0-9]{1,12}(\\.[0-9]{1,12})?$") ? plain : null;
    }

    /**
     * RFC 3339 UTC rendering of an instant, truncated to milliseconds (the contract's meaningful
     * precision). Truncating also keeps the content hash stable between an in-memory value and the
     * same value read back from a microsecond-precision column. Null renders as the epoch.
     */
    private static String instant(Instant value) {
        return (value == null ? Instant.EPOCH : value).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
    }
}
