package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.LuhnGuard;
import com.posgateway.aml.edge.tunnel.TunnelTime;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.service.decision.CanonicalDecision;
import com.posgateway.aml.util.PanHashGuard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Maps a {@link LegacyRow} to the contract's back-fill row
 * ({@code tunnel/ops/ledger.backfill.params.schema.json#/$defs/backfillRow}). Pure and deterministic:
 * the same legacy row always maps to the same JSON, so a re-sent batch is an exact duplicate on the
 * Edge and the verification sample can re-derive what was sent.
 *
 * <p><b>Stripped (contract §2.1 class S and §2.4).</b> Raw PAN, CVV/CVC, track data, PIN block and
 * expiry never leave the cloud: {@code transactions.iso_msg} and {@code emv_tags} (which carry them)
 * are never read; {@code customer_email} is never read; the feature bag goes through
 * {@link #hygiene}, the same rules the Edge applies. A {@code pan_hash} that is not a hash (raw PAN,
 * the {@code UNKNOWN} placeholder, wrong shape) is dropped, as is any field the Luhn guard flags.
 *
 * <p><b>Field mapping.</b> Values the contract field cannot hold (a 3-letter-plus asset code, an
 * amount with more decimals than the currency, a malformed merchant id, a negative amount) are not
 * lost: they travel as {@code legacy_*} / {@code amount_*} features, under the same hygiene.
 */
public final class LegacyRowMapper {

    /** §2.4 step 1 keys. */
    static final Set<String> FORBIDDEN_KEYS = Set.of(
            "pan", "pan_hash", "card_number", "pan_masked_full", "cvv", "cvv2", "cvc", "cvc2",
            "track1", "track2", "track_data", "pin", "pin_block", "expiry", "exp_date", "card_expiry");

    private static final Pattern FEATURE_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Pattern MERCHANT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
    private static final Pattern PAN_HASH = Pattern.compile("^[A-Za-z0-9:+/=_-]{16,128}$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern COUNTRY2 = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern COUNTRY3 = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern MCC = Pattern.compile("^[0-9]{4}$");
    private static final Pattern CHANNEL = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final int MAX_STRING = 512;
    private static final int MAX_FEATURES = 256;
    private static final int MAX_RULES = 512;
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    /** ISO 3166 alpha-3 to alpha-2, built once from the JDK's locale data. */
    private static final Map<String, String> ALPHA3 = alpha3();

    /** Not instantiable. */
    private LegacyRowMapper() {
    }

    /**
     * The contract row of {@code row}.
     *
     * @param rules resolves legacy rule names to rule definition ids (PSP's own first, then global)
     */
    public static ObjectNode toBackfillRow(LegacyRow row, RuleNameIndex rules) {
        ObjectNode n = F.objectNode();
        Map<String, Object> extra = new LinkedHashMap<>();
        n.put("legacyTransactionId", row.legacyTransactionId());
        n.put("edgeAssessmentId", row.edgeAssessmentId().toString());
        String txnRef = txnRef(row);
        if (txnRef != null) {
            n.put("txnRef", txnRef);
        }
        n.put("evaluatedAt", TunnelTime.formatMillis(row.evaluatedAt()));
        String panHash = panHash(row.panHash());
        if (panHash != null) {
            n.put("panHash", panHash);
        }
        amount(row, n, extra);
        merchant(row.merchantId(), n, extra);
        country(row.countryCode(), n, extra);
        channel(row.channel(), n, extra);
        Object mcc = row.features().get("mcc");
        if (mcc instanceof String s && MCC.matcher(s).matches()) {
            n.put("mcc", s);
        }

        Map<String, Object> bag = new TreeMap<>(row.features());
        bag.putAll(extra);
        n.set("features", hygiene(bag));

        n.put("decision", decision(row.decision()));
        n.put("score", score(row));
        List<String> reasons = new ArrayList<>();
        n.set("triggeredRuleIds", ruleIds(row, rules, reasons));
        ArrayNode versions = n.putArray("ruleVersionIds");
        new LinkedHashSet<>(row.assessmentVersionIds()).stream().filter(v -> v != null && v >= 1).limit(MAX_RULES)
                .forEach(versions::add);
        for (String signal : row.riskSignals()) {
            reasons.add("Legacy risk signal " + signal);
        }
        ArrayNode reasonArray = n.putArray("reasons");
        reasons.stream().map(LegacyRowMapper::reasonText).filter(r -> r != null).distinct().limit(MAX_RULES)
                .forEach(reasonArray::add);
        n.put("ctrRequired", row.ctrRequired());
        return n;
    }

    /**
     * §2.4 hygiene of a feature bag, as the Edge applies it: card-secret keys and {@code pan_hash}
     * dropped, keys outside {@code ^[a-z][a-z0-9_]{0,63}$} dropped, non-scalar values and strings over
     * 512 characters dropped, PAN-like strings and integral numbers dropped, at most 256 entries kept in
     * key order.
     */
    public static ObjectNode hygiene(Map<String, Object> bag) {
        ObjectNode out = F.objectNode();
        for (Map.Entry<String, Object> e : new TreeMap<>(bag).entrySet()) {
            if (out.size() >= MAX_FEATURES) {
                break;
            }
            String key = e.getKey();
            if (FORBIDDEN_KEYS.contains(key) || !FEATURE_KEY.matcher(key).matches()) {
                continue;
            }
            Object v = e.getValue();
            if (v == null) {
                out.putNull(key);
            } else if (v instanceof Boolean b) {
                out.put(key, b);
            } else if (v instanceof CharSequence cs) {
                String s = cs.toString();
                if (s.length() <= MAX_STRING && !LuhnGuard.isPanLike(s)) {
                    out.put(key, s);
                }
            } else if (v instanceof Number num) {
                putNumber(out, key, num);
            }
        }
        return out;
    }

    // ── fields ───────────────────────────────────────────────────────────────────────────────────

    /** txnRef: the assessment's, else the PSP's own reference when it is a valid, non-PAN txnRef. */
    static String txnRef(LegacyRow row) {
        String a = safeRef(row.assessmentTxnRef());
        return a != null ? a : safeRef(row.clientReference());
    }

    /** A txnRef that the contract accepts (1..128 printable ASCII, not PAN-like), else null. */
    private static String safeRef(String candidate) {
        String ref = TransactionReference.safeTxnRef(candidate);
        return ref == null || LuhnGuard.isPanLike(ref) ? null : ref;
    }

    /** A hash-shaped, non-PAN card hash; anything else (raw PAN, placeholder) is stripped. */
    static String panHash(String raw) {
        if (raw == null || "UNKNOWN".equals(raw) || !PAN_HASH.matcher(raw).matches()
                || LuhnGuard.isPanLike(raw) || PanHashGuard.looksLikeRawPan(raw)) {
            return null;
        }
        return raw;
    }

    /** amountMinor + currency when representable exactly, else amount features. */
    private static void amount(LegacyRow row, ObjectNode n, Map<String, Object> extra) {
        String currency = row.currency() == null ? null : row.currency().toUpperCase(Locale.ROOT);
        boolean iso = currency != null && CURRENCY.matcher(currency).matches() && isoCurrency(currency) != null;
        if (row.amountMinor() != null) {
            if (row.amountMinor() >= 0) {
                n.put("amountMinor", row.amountMinor());
            } else {
                extra.put("amount_minor_signed", String.valueOf(row.amountMinor()));
            }
            if (iso || (currency != null && CURRENCY.matcher(currency).matches())) {
                n.put("currency", currency);
            } else if (currency != null) {
                extra.put("amount_currency", currency);
            }
            return;
        }
        if (row.amountDecimal() == null) {
            if (currency != null) {
                extra.put("amount_currency", currency);
            }
            return;
        }
        Currency c = iso ? isoCurrency(currency) : null;
        if (c != null && c.getDefaultFractionDigits() >= 0 && row.amountDecimal().signum() >= 0) {
            BigDecimal minor = row.amountDecimal().movePointRight(c.getDefaultFractionDigits());
            if (minor.stripTrailingZeros().scale() <= 0 && minor.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
                n.put("amountMinor", minor.longValueExact());
                n.put("currency", currency);
                return;
            }
        }
        extra.put("amount_decimal", row.amountDecimal().stripTrailingZeros().toPlainString());
        if (currency != null) {
            extra.put("amount_currency", currency);
        }
    }

    /** The JDK currency for an ISO 4217 code, or null. */
    private static Currency isoCurrency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** merchantId when it fits the master-data id shape, else a legacy feature. */
    private static void merchant(String merchantId, ObjectNode n, Map<String, Object> extra) {
        if (merchantId == null || merchantId.isBlank()) {
            return;
        }
        String id = merchantId.trim();
        if (MERCHANT.matcher(id).matches() && !LuhnGuard.isPanLike(id)) {
            n.put("merchantId", id);
        } else {
            extra.put("legacy_merchant_ref", id);
        }
    }

    /** countryCode (alpha-2, or alpha-3 converted), else a legacy feature. */
    private static void country(String raw, ObjectNode n, Map<String, Object> extra) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (COUNTRY2.matcher(c).matches()) {
            n.put("countryCode", c);
        } else if (COUNTRY3.matcher(c).matches() && ALPHA3.containsKey(c)) {
            n.put("countryCode", ALPHA3.get(c));
        } else {
            extra.put("legacy_country", c);
        }
    }

    /** channel upper-cased with separators as underscores, else a legacy feature. */
    private static void channel(String raw, ObjectNode n, Map<String, Object> extra) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String c = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (CHANNEL.matcher(c).matches()) {
            n.put("channel", c);
        } else {
            extra.put("legacy_channel", raw.trim());
        }
    }

    /** ALLOW / ALERT / HOLD / BLOCK from any legacy value (REVIEW and FLAG are ALERT, SUSPEND is HOLD). */
    static String decision(String raw) {
        return CanonicalDecision.toLegacyAction(CanonicalDecision.fromLegacyAction(raw));
    }

    /**
     * Integer score ≥ 0: the assessment's v2 score; else, for card rows, the legacy model score
     * (a probability in [0, 1] is scaled to points 0..100, anything larger is already points); for
     * multi-asset rows the stored risk score; 0 when nothing was stored.
     */
    static int score(LegacyRow row) {
        if (row.assessmentScore() != null) {
            return Math.max(0, row.assessmentScore());
        }
        Double s = row.legacyScore();
        if (s == null || s.isNaN() || s.isInfinite() || s <= 0) {
            return 0;
        }
        if (row.source() == LegacySource.TXN && s <= 1.0) {
            return (int) Math.round(s * 100.0);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(s));
    }

    /**
     * Rule ids: the assessment's v2 ids; else legacy rule names resolved to rule definition ids.
     * Names that resolve to nothing always become a reason ({@code "Legacy rule <name>"}), so no
     * evidence is lost.
     */
    private static ArrayNode ruleIds(LegacyRow row, RuleNameIndex rules, List<String> reasons) {
        ArrayNode out = F.arrayNode();
        Set<Long> ids = new LinkedHashSet<>();
        row.assessmentRuleIds().stream().filter(id -> id != null && id >= 1).forEach(ids::add);
        boolean fromAssessment = !ids.isEmpty();
        // Unresolved names always become reasons, resolved ones add ids only when the assessment has
        // none: so the row maps identically before and after its minted decision record exists.
        for (String name : row.triggeredRuleNames()) {
            Long id = rules.idOf(name);
            if (id == null || id < 1) {
                reasons.add("Legacy rule " + name);
            } else if (!fromAssessment) {
                ids.add(id);
            }
        }
        ids.stream().limit(MAX_RULES).forEach(out::add);
        return out;
    }

    /** A reason as the Edge accepts it: control characters removed, ≤ 512 chars, never PAN-like. */
    private static String reasonText(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("\\p{Cntrl}", " ").strip();
        if (s.length() > MAX_STRING) {
            s = s.substring(0, MAX_STRING);
        }
        return s.isEmpty() || LuhnGuard.isPanLike(s) ? null : s;
    }

    /** A number feature; integral values the Luhn guard flags are dropped, like the Edge does. */
    private static void putNumber(ObjectNode out, String key, Number num) {
        if (num instanceof Double d && (d.isNaN() || d.isInfinite()
                || (d == Math.rint(d) && Math.abs(d) < 1e19 && LuhnGuard.isPanLike(Long.toString(d.longValue()))))) {
            return;
        }
        if (num instanceof Float fl && (fl.isNaN() || fl.isInfinite())) {
            return;
        }
        if (num instanceof Integer || num instanceof Long || num instanceof Short || num instanceof Byte
                || num instanceof java.math.BigInteger) {
            if (LuhnGuard.isPanLike(num.toString())) {
                return;
            }
            out.put(key, num.longValue());
            return;
        }
        if (num instanceof BigDecimal bd) {
            if (bd.stripTrailingZeros().scale() <= 0 && LuhnGuard.isPanLike(bd.toBigInteger().toString())) {
                return;
            }
            out.put(key, bd);
            return;
        }
        out.put(key, num.doubleValue());
    }

    /** ISO alpha-3 → alpha-2 table. */
    private static Map<String, String> alpha3() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String a2 : Locale.getISOCountries()) {
            try {
                m.put(new Locale("", a2).getISO3Country(), a2);
            } catch (java.util.MissingResourceException e) {
                // A region without an alpha-3 code cannot be mapped; it is simply not in the table.
                continue;
            }
        }
        return Map.copyOf(m);
    }

    /** Resolves legacy rule names (as the cloud rule engines reported them) to rule definition ids. */
    @FunctionalInterface
    public interface RuleNameIndex {

        /** The id of the rule named {@code name}, or {@code null}. */
        Long idOf(String name);

        /** An index that resolves nothing (every name becomes a reason). */
        RuleNameIndex NONE = name -> null;

        /** An index over a name → id map. */
        static RuleNameIndex of(Map<String, Long> byName) {
            Map<String, Long> copy = Map.copyOf(byName);
            return copy::get;
        }
    }
}
