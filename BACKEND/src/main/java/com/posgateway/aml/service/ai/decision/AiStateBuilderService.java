package com.posgateway.aml.service.ai.decision;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds allow-listed Jev state per decision point. Never forwards raw feature maps.
 */
@Service
public class AiStateBuilderService {

    static final Set<String> FORBIDDEN_KEYS = Set.of(
            "pan", "pan_hash", "card_number", "card_bin_hash", "cvv", "pin", "emv", "aid", "aip", "cvm",
            "terminal_id", "ip", "ip_address", "device_fingerprint", "device_id",
            "merchant_id", "customer_id", "account_number", "account_no", "iban", "swift", "wallet",
            "national_id", "passport", "ssn", "tax_id", "email", "phone",
            "customer_name", "beneficiary_name", "sender_name", "receiver_name",
            "legal_name", "trading_name", "screened_name", "name", "matches",
            "description", "message", "notes", "operatorprompt", "operator_prompt",
            "website", "matchedkeyword", "matched_keyword",
            "created_at", "updated_at", "timestamp", "txn_id", "alert_id", "case_id"
    );

    public Map<String, Object> buildState(AiDecisionPoint decisionPoint, AiDecisionContext context) {
        Map<String, Object> features = context.getFeatures() != null ? context.getFeatures() : Map.of();
        Map<String, Object> state = switch (decisionPoint) {
            case DP1_TM_ALERT_TRIAGE -> buildDp1(features, context);
            case DP2_SCREENING_MATCH -> buildDp2(features, context);
            case DP3_CASE_TRIAGE -> buildDp3(features, context);
            case DP4_CUSTOMER_RISK -> buildDp4(features, context);
            case DP5_SAR_NARRATIVE -> buildDp5(features, context);
        };
        return stripForbiddenDeep(state);
    }

    public String hashState(Map<String, Object> state) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(state.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildDp1(Map<String, Object> features, AiDecisionContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> alert = new LinkedHashMap<>();
        putSafe(alert, "source_type", firstNonNull(features, "source_type", "TRANSACTION_MONITORING"));
        putSafe(alert, "engine_action", firstNonNull(features, "engine_action", features.get("action"), context.getBaselineDecision()));
        putSafe(alert, "severity", features.get("severity"));
        putSafe(alert, "rules_triggered", sanitizeRulesTriggered(features.get("rules_triggered"), features.get("reasons")));
        putSafe(alert, "ml_score", firstNonNull(features, "ml_score", features.get("score")));
        putSafe(alert, "ml_hold_threshold", features.get("ml_hold_threshold"));
        putSafe(alert, "anomaly_score", features.get("anomaly_score"));
        putSafe(alert, "sanctions_screening", firstNonNull(features, "sanctions_screening", "UNKNOWN"));
        putSafe(alert, "sar_required_flag", features.get("sar_required_flag"));
        putSafe(alert, "ctr_required_flag", features.get("ctr_required_flag"));
        state.put("alert", alert);

        Map<String, Object> subject = new LinkedHashMap<>();
        if (context.getMerchantId() != null) {
            subject.put("merchant_ref", "M-" + context.getMerchantId());
        }
        putSafe(subject, "subject_type", firstNonNull(features, "subject_type", "MERCHANT"));
        putSafe(subject, "mcc", features.get("mcc"));
        putSafe(subject, "mcc_description", features.get("mcc_description"));
        putSafe(subject, "declared_business", features.get("declared_business_category"));
        putSafe(subject, "country", firstNonNull(features, "country", features.get("country_code")));
        putSafe(subject, "account_age_months", features.get("account_age_months"));
        putSafe(subject, "declared_expected_monthly_volume_kes", features.get("declared_expected_monthly_volume_kes"));
        putSafe(subject, "actual_30d_volume_kes", features.get("actual_30d_volume_kes"));
        putSafe(subject, "volume_vs_expected_ratio", features.get("volume_vs_expected_ratio"));
        putSafe(subject, "cra_score_0_100", features.get("cra_score_0_100"));
        putSafe(subject, "cra_level", features.get("cra_level"));
        putSafe(subject, "pep_flag", features.get("pep_flag"));
        putSafe(subject, "prior_alerts_90d", features.get("prior_alerts_90d"));
        putSafe(subject, "prior_alert_dispositions", features.get("prior_alert_dispositions"));
        putSafe(subject, "open_case", features.get("open_case"));
        putSafe(subject, "kyc_status", features.get("kyc_status"));
        putSafe(subject, "ubo_count", features.get("ubo_count"));
        state.put("subject", subject);

        Map<String, Object> activity = new LinkedHashMap<>();
        copyKnown(activity, features, List.of(
                "inbound_count", "inbound_total_kes", "outbound_count", "outbound_total_kes",
                "outbound_distinct_destinations", "outbound_destination_type", "round_amount_share",
                "median_minutes_inbound_to_outbound", "night_time_share_00_05", "ip_vpn_or_proxy_share",
                "cross_border_share", "high_risk_country_exposure"));
        state.put("activity_window_7d", activity);
        return state;
    }

    private Map<String, Object> buildDp2(Map<String, Object> features, AiDecisionContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> screened = new LinkedHashMap<>();
        putSafe(screened, "entity_type", features.get("screened_entity_type"));
        putSafe(screened, "nationality", features.get("screened_nationality"));
        putSafe(screened, "registration_number_present", features.get("screened_registration_present"));
        state.put("screened_party_attributes", screened);

        Map<String, Object> listEntry = new LinkedHashMap<>();
        putSafe(listEntry, "list_type", features.get("list_type"));
        putSafe(listEntry, "pep_level", features.get("pep_level"));
        putSafe(listEntry, "program_category", features.get("program_category"));
        state.put("list_entry_attributes", listEntry);

        Map<String, Object> matchFeatures = new LinkedHashMap<>();
        putSafe(matchFeatures, "similarity_score", features.get("similarity_score"));
        putSafe(matchFeatures, "match_type", features.get("match_type"));
        putSafe(matchFeatures, "alias_flag", features.get("alias_flag"));
        putSafe(matchFeatures, "dob_relation", features.get("dob_relation"));
        putSafe(matchFeatures, "nationality_relation", features.get("nationality_relation"));
        putSafe(matchFeatures, "registration_number_relation", features.get("registration_number_relation"));
        putSafe(matchFeatures, "match_count", features.get("match_count"));
        state.put("match_features", matchFeatures);
        return state;
    }

    private Map<String, Object> buildDp3(Map<String, Object> features, AiDecisionContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> caseSummary = new LinkedHashMap<>();
        putSafe(caseSummary, "alert_type", features.get("alertType"));
        putSafe(caseSummary, "rule_name", features.get("ruleName"));
        putSafe(caseSummary, "score", features.get("score"));
        putSafe(caseSummary, "priority", features.get("priority"));
        putSafe(caseSummary, "status", context.getBaselineDecision());
        state.put("case_summary", caseSummary);

        Map<String, Object> alertsSummary = new LinkedHashMap<>();
        putSafe(alertsSummary, "trigger_codes", features.get("trigger_codes"));
        putSafe(alertsSummary, "typology_tags", features.get("typology_tags"));
        state.put("alerts_summary", alertsSummary);

        Map<String, Object> subject = new LinkedHashMap<>();
        if (context.getMerchantId() != null) {
            subject.put("merchant_ref", "M-" + context.getMerchantId());
        }
        putSafe(subject, "kyc_status", features.get("kyc_status"));
        state.put("subject", subject);

        Map<String, Object> investigation = new LinkedHashMap<>();
        putSafe(investigation, "rfi_outcomes", features.get("rfi_outcomes"));
        putSafe(investigation, "customer_explanation_category", features.get("customer_explanation_category"));
        state.put("investigation_facts", investigation);
        return state;
    }

    private Map<String, Object> buildDp4(Map<String, Object> features, AiDecisionContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        if (context.getMerchantId() != null) {
            profile.put("customer_ref", "M-" + context.getMerchantId());
        }
        putSafe(profile, "pep_flag", features.get("pep_flag"));
        putSafe(profile, "country", firstNonNull(features, "country", features.get("country_code")));
        putSafe(profile, "sector_mcc", features.get("mcc"));
        state.put("customer_profile", profile);

        Map<String, Object> components = new LinkedHashMap<>();
        putSafe(components, "cra_score_0_100", features.get("cra_score_0_100"));
        putSafe(components, "cra_level", features.get("cra_level"));
        putSafe(components, "deterministic_risk_tier", features.get("deterministic_risk_tier"));
        state.put("risk_components", components);

        Map<String, Object> history = new LinkedHashMap<>();
        putSafe(history, "prior_alerts_90d", features.get("prior_alerts_90d"));
        putSafe(history, "open_edd", features.get("open_edd"));
        state.put("history_summary", history);
        return state;
    }

    private Map<String, Object> buildDp5(Map<String, Object> features, AiDecisionContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        putSafe(state, "case_facts", features.get("case_facts"));
        putSafe(state, "draft_narrative", features.get("draft_narrative"));
        return state;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> sanitizeRulesTriggered(Object rulesTriggered, Object reasons) {
        if (rulesTriggered instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> sanitized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    putSafe(rule, "code", map.get("code"));
                    putSafe(rule, "name", map.get("name"));
                    putSafe(rule, "detail", map.get("detail"));
                    sanitized.add(stripForbiddenDeep(rule));
                }
            }
            return sanitized;
        }
        if (reasons instanceof List<?> reasonList) {
            List<Map<String, Object>> sanitized = new ArrayList<>();
            for (Object reason : reasonList) {
                if (reason != null) {
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("detail", String.valueOf(reason));
                    sanitized.add(rule);
                }
            }
            return sanitized;
        }
        return List.of();
    }

    static void copyKnown(Map<String, Object> target, Map<String, Object> source, List<String> keys) {
        for (String key : keys) {
            putSafe(target, key, source.get(key));
        }
    }

    static void putSafe(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    static Object firstNonNull(Map<String, Object> map, String primaryKey, Object... fallbacks) {
        Object primary = map.get(primaryKey);
        if (primary != null) {
            return primary;
        }
        for (Object fallback : fallbacks) {
            if (fallback != null) {
                return fallback;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> stripForbiddenDeep(Map<String, Object> input) {
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null || isForbiddenKey(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                output.put(key, stripForbiddenDeep((Map<String, Object>) nested));
            } else if (value instanceof List<?> list) {
                output.put(key, stripForbiddenList(list));
            } else {
                output.put(key, value);
            }
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> stripForbiddenList(List<?> list) {
        List<Object> output = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> nested) {
                output.add(stripForbiddenDeep((Map<String, Object>) nested));
            } else {
                output.add(item);
            }
        }
        return output;
    }

    static boolean isForbiddenKey(String key) {
        return FORBIDDEN_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }
}
