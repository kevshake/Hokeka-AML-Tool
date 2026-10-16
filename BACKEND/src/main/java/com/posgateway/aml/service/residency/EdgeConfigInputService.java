package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.residency.EdgeLimit;
import com.posgateway.aml.entity.residency.EdgeMerchantBlocklistEntry;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.residency.EdgeLimitRepository;
import com.posgateway.aml.repository.residency.EdgeMerchantBlocklistRepository;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.edge.records.DecisionRecordV2Validator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Edits the Edge config inputs that had no cloud source before R3: CTR and tunnel settings
 * ({@code psp_edge_settings}), Edge limits ({@code edge_limits}) and the merchant blocklist
 * ({@code edge_merchant_blocklist}). Every edit is validated against the contract's section schema,
 * audited, and announced with {@link PspDataSettingsChangedEvent} so a new configVersion is issued.
 */
@Service
public class EdgeConfigInputService {

    /** Every tunnel op of the contract (common.schema.json#/$defs/op). */
    static final Set<String> TUNNEL_OPS = Set.of("txn.search", "txn.get", "txn.batchGet", "txn.related",
            "filing.extract", "export.stream", "ledger.backfill", "ledger.health");
    private static final Set<String> SCOPES = Set.of("CARD", "MERCHANT", "PSP");
    private static final Set<String> METRICS = Set.of("COUNT", "AMOUNT");
    private static final Set<String> WINDOWS = Set.of("PT1H", "P1D", "P7D", "P30D");
    private static final Set<String> ACTIONS = Set.of("ALERT", "HOLD", "BLOCK");
    private static final Set<String> BLOCK_REASONS = Set.of("SANCTIONED", "REGULATOR_ORDER", "PSP_TERMINATED", "FRAUD_CONFIRMED");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern CHANNEL = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern MCC = Pattern.compile("^[0-9]{4}$");
    private static final Pattern MERCHANT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
    private static final Pattern TIMEZONE = Pattern.compile("^[A-Za-z]+(/[A-Za-z0-9_+-]+){0,2}$");

    private final PspRepository psps;
    private final PspEdgeSettingsRepository settings;
    private final EdgeLimitRepository limits;
    private final EdgeMerchantBlocklistRepository blocklist;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    public EdgeConfigInputService(PspRepository psps,
                                  PspEdgeSettingsRepository settings,
                                  EdgeLimitRepository limits,
                                  EdgeMerchantBlocklistRepository blocklist,
                                  AuditLogService audit,
                                  ApplicationEventPublisher events) {
        this.psps = psps;
        this.settings = settings;
        this.limits = limits;
        this.blocklist = blocklist;
        this.audit = audit;
        this.events = events;
    }

    /** CTR + tunnel settings change request; null members keep their current value. */
    public record SettingsUpdate(Boolean ctrEnabled, String ctrBusinessDayTimezone, String ctrReportingCurrency,
                                 Long ctrThresholdMinor, List<String> ctrCashChannels, Boolean tunnelEnabled,
                                 List<String> tunnelOpsAllowed, Boolean tunnelBackfillAllowed,
                                 Integer tunnelMaxInflight) {}

    /** A new Edge limit. */
    public record LimitRequest(String scope, String metric, String window, String currency, Long max,
                               String action, String merchantId, String mcc) {}

    /** A new merchant block. */
    public record BlockRequest(String merchantId, String reasonCode) {}

    /** The PSP's CTR and tunnel settings (contract defaults when none were saved). */
    @Transactional(readOnly = true)
    public PspEdgeSettings settings(Long pspId) {
        requirePsp(pspId);
        return settings.findById(pspId).orElseGet(() -> PspEdgeSettings.defaultsFor(pspId));
    }

    /** Validates and saves CTR / tunnel settings. */
    @Transactional
    public PspEdgeSettings updateSettings(Long pspId, SettingsUpdate update, User editor) {
        requirePsp(pspId);
        if (update == null) {
            throw new IllegalArgumentException("settings body is required");
        }
        PspEdgeSettings row = settings.findById(pspId).orElseGet(() -> PspEdgeSettings.defaultsFor(pspId));
        Map<String, Object> before = snapshot(row);
        if (update.ctrEnabled() != null) row.setCtrEnabled(update.ctrEnabled());
        if (update.ctrBusinessDayTimezone() != null) row.setCtrBusinessDayTimezone(timezone(update.ctrBusinessDayTimezone()));
        if (update.ctrReportingCurrency() != null) row.setCtrReportingCurrency(currency(update.ctrReportingCurrency(), "ctrReportingCurrency"));
        if (update.ctrThresholdMinor() != null) {
            if (update.ctrThresholdMinor() < 0) {
                throw new IllegalArgumentException("ctrThresholdMinor must be >= 0");
            }
            row.setCtrThresholdMinor(update.ctrThresholdMinor());
        }
        if (update.ctrCashChannels() != null) row.setCtrCashChannels(channels(update.ctrCashChannels()));
        if (update.tunnelEnabled() != null) row.setTunnelEnabled(update.tunnelEnabled());
        if (update.tunnelOpsAllowed() != null) row.setTunnelOpsAllowed(ops(update.tunnelOpsAllowed()));
        if (update.tunnelBackfillAllowed() != null) row.setTunnelBackfillAllowed(update.tunnelBackfillAllowed());
        if (update.tunnelMaxInflight() != null) {
            if (update.tunnelMaxInflight() < 1 || update.tunnelMaxInflight() > 16) {
                throw new IllegalArgumentException("tunnelMaxInflight must be between 1 and 16");
            }
            row.setTunnelMaxInflight(update.tunnelMaxInflight());
        }
        // The platform CTR threshold is in USD; any other reporting currency needs its own threshold.
        if (row.isCtrEnabled() && !"USD".equals(row.getCtrReportingCurrency()) && row.getCtrThresholdMinor() == null) {
            throw new IllegalArgumentException("ctrThresholdMinor is required when the reporting currency is not USD");
        }
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(editor.getUsername());
        PspEdgeSettings saved = settings.save(row);
        audit.logAction(editor, "EDGE_CONFIG_SETTINGS_UPDATED", "PSP", String.valueOf(pspId),
                before, snapshot(saved), null, "Edge CTR / tunnel settings changed");
        events.publishEvent(new PspDataSettingsChangedEvent(pspId, "EDGE_SETTINGS"));
        return saved;
    }

    /** Active Edge limits of the PSP. */
    @Transactional(readOnly = true)
    public List<EdgeLimit> limits(Long pspId) {
        requirePsp(pspId);
        return limits.findByPspIdAndActiveTrueOrderByIdAsc(pspId);
    }

    /** Validates and adds an Edge limit. */
    @Transactional
    public EdgeLimit addLimit(Long pspId, LimitRequest request, User editor) {
        requirePsp(pspId);
        if (request == null) {
            throw new IllegalArgumentException("limit body is required");
        }
        EdgeLimit limit = new EdgeLimit();
        limit.setPspId(pspId);
        limit.setScope(oneOf(request.scope(), SCOPES, "scope"));
        limit.setMetric(oneOf(request.metric(), METRICS, "metric"));
        limit.setWindowCode(oneOf(request.window(), WINDOWS, "window"));
        limit.setAction(oneOf(request.action(), ACTIONS, "action"));
        if (request.max() == null || request.max() < 0) {
            throw new IllegalArgumentException("max must be a non-negative integer");
        }
        limit.setMaxValue(request.max());
        if (request.currency() != null) {
            limit.setCurrency(currency(request.currency(), "currency"));
        }
        if ("AMOUNT".equals(limit.getMetric()) && limit.getCurrency() == null) {
            throw new IllegalArgumentException("currency is required for an AMOUNT limit");
        }
        if (request.merchantId() != null) {
            limit.setMerchantId(merchant(request.merchantId()));
        }
        if (request.mcc() != null) {
            if (!MCC.matcher(request.mcc()).matches()) {
                throw new IllegalArgumentException("mcc must be 4 digits");
            }
            limit.setMcc(request.mcc());
        }
        limit.setCreatedBy(editor.getUsername());
        EdgeLimit saved = limits.save(limit);
        audit.logAction(editor, "EDGE_LIMIT_ADDED", "EDGE_LIMIT", String.valueOf(saved.getId()), null,
                limitSnapshot(saved), null, "Edge limit added for PSP " + pspId);
        events.publishEvent(new PspDataSettingsChangedEvent(pspId, "LIMITS"));
        return saved;
    }

    /** Retires an Edge limit (kept for audit, no longer distributed). */
    @Transactional
    public void retireLimit(Long pspId, Long limitId, User editor) {
        EdgeLimit limit = limits.findByIdAndPspId(limitId, pspId)
                .filter(EdgeLimit::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edge limit not found"));
        limit.setActive(false);
        limit.setUpdatedAt(Instant.now());
        limits.save(limit);
        audit.logAction(editor, "EDGE_LIMIT_RETIRED", "EDGE_LIMIT", String.valueOf(limitId), limitSnapshot(limit),
                null, null, "Edge limit retired for PSP " + pspId);
        events.publishEvent(new PspDataSettingsChangedEvent(pspId, "LIMITS"));
    }

    /** Active merchant blocks of the PSP. */
    @Transactional(readOnly = true)
    public List<EdgeMerchantBlocklistEntry> blocklist(Long pspId) {
        requirePsp(pspId);
        return blocklist.findByPspIdAndRemovedAtIsNullOrderByMerchantIdAsc(pspId);
    }

    /** Blocks a merchant on the PSP's Edges. */
    @Transactional
    public EdgeMerchantBlocklistEntry block(Long pspId, BlockRequest request, User editor) {
        requirePsp(pspId);
        if (request == null) {
            throw new IllegalArgumentException("block body is required");
        }
        String merchantId = merchant(request.merchantId());
        String reason = oneOf(request.reasonCode(), BLOCK_REASONS, "reasonCode");
        if (blocklist.findFirstByPspIdAndMerchantIdAndRemovedAtIsNull(pspId, merchantId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant is already blocked");
        }
        EdgeMerchantBlocklistEntry entry = new EdgeMerchantBlocklistEntry();
        entry.setPspId(pspId);
        entry.setMerchantId(merchantId);
        entry.setReasonCode(reason);
        entry.setSince(Instant.now());
        entry.setCreatedBy(editor.getUsername());
        EdgeMerchantBlocklistEntry saved = blocklist.save(entry);
        audit.logAction(editor, "EDGE_MERCHANT_BLOCKED", "MERCHANT", merchantId, null,
                Map.of("pspId", pspId, "merchantId", merchantId, "reasonCode", reason), null,
                "Merchant blocked on the Edge");
        events.publishEvent(new PspDataSettingsChangedEvent(pspId, "MERCHANT_BLOCKLIST"));
        return saved;
    }

    /** Lifts a merchant block (the row stays, with removedAt). */
    @Transactional
    public void unblock(Long pspId, String merchantId, User editor) {
        EdgeMerchantBlocklistEntry entry = blocklist.findFirstByPspIdAndMerchantIdAndRemovedAtIsNull(pspId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant is not blocked"));
        entry.setRemovedAt(Instant.now());
        entry.setRemovedBy(editor.getUsername());
        blocklist.save(entry);
        audit.logAction(editor, "EDGE_MERCHANT_UNBLOCKED", "MERCHANT", merchantId,
                Map.of("pspId", pspId, "merchantId", merchantId, "reasonCode", entry.getReasonCode()), null, null,
                "Merchant block lifted on the Edge");
        events.publishEvent(new PspDataSettingsChangedEvent(pspId, "MERCHANT_BLOCKLIST"));
    }

    // ── validation helpers ───────────────────────────────────────────────────────────────────────

    /** 404 unless the PSP exists. */
    private void requirePsp(Long pspId) {
        if (pspId == null || !psps.existsById(pspId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PSP not found");
        }
    }

    /** Upper-cased member of {@code allowed}, or 400. */
    private static String oneOf(String value, Set<String> allowed, String field) {
        String normalised = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalised == null || !allowed.contains(normalised)) {
            throw new IllegalArgumentException(field + " must be one of " + allowed);
        }
        return normalised;
    }

    /** ISO 4217-shaped currency, or 400. */
    private static String currency(String value, String field) {
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(normalised).matches()) {
            throw new IllegalArgumentException(field + " must be a 3-letter currency code");
        }
        return normalised;
    }

    /** A real IANA zone id in the schema's pattern, or 400. */
    private static String timezone(String value) {
        String zone = value.trim();
        if (!TIMEZONE.matcher(zone).matches()) {
            throw new IllegalArgumentException("ctrBusinessDayTimezone must be an IANA zone id such as Africa/Nairobi");
        }
        try {
            ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("ctrBusinessDayTimezone is not a known time zone");
        }
        return zone;
    }

    /** Unique channel codes (schema channel pattern, at most 32), or 400. */
    private static List<String> channels(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            String channel = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (!CHANNEL.matcher(channel).matches()) {
                throw new IllegalArgumentException("cash channel codes must match " + CHANNEL.pattern());
            }
            out.add(channel);
        }
        if (out.size() > 32) {
            throw new IllegalArgumentException("at most 32 cash channels");
        }
        return new ArrayList<>(out);
    }

    /** Unique contract ops, or 400. */
    private static List<String> ops(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !TUNNEL_OPS.contains(value.trim())) {
                throw new IllegalArgumentException("tunnel ops must be among " + TUNNEL_OPS);
            }
            out.add(value.trim());
        }
        return new ArrayList<>(out);
    }

    /** Merchant id in the schema pattern and never PAN-like, or 400. */
    private static String merchant(String value) {
        String id = value == null ? "" : value.trim();
        if (!MERCHANT.matcher(id).matches()) {
            throw new IllegalArgumentException("merchantId is malformed");
        }
        if (DecisionRecordV2Validator.looksLikePan(id)) {
            throw new IllegalArgumentException("merchantId looks like a card number");
        }
        return id;
    }

    /** Audit snapshot of the settings row. */
    private static Map<String, Object> snapshot(PspEdgeSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ctrEnabled", s.isCtrEnabled());
        m.put("ctrBusinessDayTimezone", s.getCtrBusinessDayTimezone());
        m.put("ctrReportingCurrency", s.getCtrReportingCurrency());
        m.put("ctrThresholdMinor", s.getCtrThresholdMinor());
        m.put("ctrCashChannels", s.getCtrCashChannels());
        m.put("tunnelEnabled", s.isTunnelEnabled());
        m.put("tunnelOpsAllowed", s.getTunnelOpsAllowed());
        m.put("tunnelBackfillAllowed", s.isTunnelBackfillAllowed());
        m.put("tunnelMaxInflight", s.getTunnelMaxInflight());
        return m;
    }

    /** Audit snapshot of a limit. */
    private static Map<String, Object> limitSnapshot(EdgeLimit l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("limitId", l.getId());
        m.put("pspId", l.getPspId());
        m.put("scope", l.getScope());
        m.put("metric", l.getMetric());
        m.put("window", l.getWindowCode());
        m.put("currency", l.getCurrency());
        m.put("max", l.getMaxValue());
        m.put("action", l.getAction());
        m.put("merchantId", l.getMerchantId());
        m.put("mcc", l.getMcc());
        m.put("active", l.isActive());
        return m;
    }
}
