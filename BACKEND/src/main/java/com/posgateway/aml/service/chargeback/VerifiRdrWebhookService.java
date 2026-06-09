package com.posgateway.aml.service.chargeback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.integration.verifi.VerifiRdrProperties;
import com.posgateway.aml.integration.verifi.VerifiWebhookSignatureVerifier;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.AerospikeMetricsRepository;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.service.case_management.CaseCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Processes Visa/Verifi RDR webhook callbacks and maps them to alerts, cases, and metrics.
 */
@Service
public class VerifiRdrWebhookService {

    private static final Logger log = LoggerFactory.getLogger(VerifiRdrWebhookService.class);

    private final VerifiRdrProperties properties;
    private final VerifiWebhookSignatureVerifier signatureVerifier;
    private final ChargebackDisputeRepository disputeRepository;
    private final AlertRepository alertRepository;
    private final MerchantRepository merchantRepository;
    private final AerospikeMetricsRepository metricsRepository;
    private final CaseCreationService caseCreationService;
    private final ObjectMapper objectMapper;

    public VerifiRdrWebhookService(VerifiRdrProperties properties,
                                   VerifiWebhookSignatureVerifier signatureVerifier,
                                   ChargebackDisputeRepository disputeRepository,
                                   AlertRepository alertRepository,
                                   MerchantRepository merchantRepository,
                                   AerospikeMetricsRepository metricsRepository,
                                   CaseCreationService caseCreationService,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.signatureVerifier = signatureVerifier;
        this.disputeRepository = disputeRepository;
        this.alertRepository = alertRepository;
        this.merchantRepository = merchantRepository;
        this.metricsRepository = metricsRepository;
        this.caseCreationService = caseCreationService;
        this.objectMapper = objectMapper;
    }

    public boolean isAuthenticated(Map<String, String> headers, Object body) {
        if (!properties.isSignatureRequired()) {
            return true;
        }
        if (signatureVerifier.verifyApiKey(headers, properties.getApiKey())) {
            return true;
        }
        String signature = firstHeader(headers,
                "X-Butter-Webhook-Signature",
                "X-Verifi-Webhook-Signature",
                "X-Webhook-Signature");
        String createdAt = firstHeader(headers,
                "X-Butter-Webhook-Created",
                "X-Verifi-Webhook-Created",
                "X-Webhook-Created");
        return signatureVerifier.verifyHmacSha256(body, signature, createdAt, properties.getWebhookSecret());
    }

    @Transactional
    public ChargebackDispute processWebhook(Map<String, String> headers, Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            log.warn("Verifi RDR webhook received but verifi.rdr.enabled=false — processing anyway for audit");
        }

        String dedupId = firstHeader(headers,
                "X-Butter-Webhook-Deduplication-ID",
                "X-Verifi-Deduplication-ID",
                "X-Webhook-Deduplication-ID");
        if (dedupId != null) {
            Optional<ChargebackDispute> existing = disputeRepository.findByDeduplicationId(dedupId);
            if (existing.isPresent()) {
                log.info("Duplicate Verifi RDR webhook ignored: {}", dedupId);
                return existing.get();
            }
        }

        String webhookType = firstHeader(headers,
                "X-Butter-Webhook-Type",
                "X-Verifi-Webhook-Type",
                "X-Webhook-Type");
        String notificationType = resolveNotificationType(webhookType, payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = payload.get("data") instanceof Map
                ? (Map<String, Object>) payload.get("data")
                : payload;

        ChargebackDispute dispute = new ChargebackDispute();
        dispute.setExternalEventId(asString(payload.get("id")));
        dispute.setDeduplicationId(dedupId);
        dispute.setNotificationType(notificationType);
        dispute.setScheme("visa");

        populateFromPayload(dispute, data, payload);

        Long merchantId = resolveMerchantId(dispute);
        dispute.setMerchantId(merchantId);
        if (merchantId != null) {
            merchantRepository.findByMerchantId(merchantId)
                    .map(Merchant::getPsp)
                    .filter(psp -> psp != null)
                    .map(psp -> psp.getPspId())
                    .ifPresent(dispute::setPspId);
        }

        try {
            dispute.setRawPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            dispute.setRawPayload(payload.toString());
        }

        Alert alert = createAlert(dispute);
        dispute.setAlertId(alert.getAlertId());

        if (properties.isAutoCreateCases() && shouldOpenCase(dispute)) {
            caseCreationService.triggerCaseFromChargeback(
                    dispute.getMerchantId(),
                    dispute.getPspId(),
                    notificationType,
                    buildCaseDescription(dispute));
        }

        if (merchantId != null && dispute.getCaseAmount() != null) {
            long amountCents = dispute.getCaseAmount().multiply(BigDecimal.valueOf(100)).longValue();
            metricsRepository.incrementCounters(String.valueOf(merchantId), false, true, amountCents);
        }

        return disputeRepository.save(dispute);
    }

    private void populateFromPayload(ChargebackDispute dispute, Map<String, Object> data, Map<String, Object> root) {
        @SuppressWarnings("unchecked")
        Map<String, Object> visaRdr = data.get("visa_rdr") instanceof Map
                ? (Map<String, Object>) data.get("visa_rdr")
                : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> caseObj = data.get("case") instanceof Map
                ? (Map<String, Object>) data.get("case")
                : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> network = data.get("network") instanceof Map
                ? (Map<String, Object>) data.get("network")
                : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> card = data.get("card") instanceof Map
                ? (Map<String, Object>) data.get("card")
                : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> pspTxn = data.get("psp_transaction") instanceof Map
                ? (Map<String, Object>) data.get("psp_transaction")
                : null;

        dispute.setRdrStatus(firstNonBlank(
                asString(visaRdr != null ? visaRdr.get("status") : null),
                asString(data.get("status")),
                asString(root.get("status"))));

        dispute.setCaseId(firstNonBlank(
                asString(visaRdr != null ? visaRdr.get("case_id") : null),
                asString(data.get("case_id")),
                asString(root.get("id"))));

        if (caseObj != null) {
            dispute.setCaseDate(parseDate(caseObj.get("date")));
            dispute.setCaseAmount(toBigDecimal(caseObj.get("amount")));
            dispute.setCaseCurrency(asString(caseObj.get("currency")));
        } else {
            dispute.setCaseAmount(toBigDecimal(data.get("case_amount")));
            dispute.setCaseCurrency(asString(data.get("case_currency")));
            dispute.setCaseDate(parseDate(data.get("case_date")));
        }

        if (network != null) {
            dispute.setNetworkMerchantId(asString(network.get("merchant_id")));
            dispute.setNetworkTransactionId(asString(network.get("transaction_id")));
            dispute.setReasonCode(asString(network.get("reason_code")));
            dispute.setMerchantOrderId(asString(network.get("merchant_order_id")));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> reason = visaRdr != null && visaRdr.get("reason") instanceof Map
                ? (Map<String, Object>) visaRdr.get("reason")
                : null;
        if (reason != null) {
            dispute.setReasonCode(firstNonBlank(dispute.getReasonCode(), asString(reason.get("code"))));
            dispute.setReasonCategory(asString(reason.get("category")));
        } else if (dispute.getReasonCode() != null) {
            dispute.setReasonCategory(categorizeReasonCode(dispute.getReasonCode()));
        }

        dispute.setAcquirerReferenceNumber(asString(data.get("acquirer_reference_number")));
        dispute.setRefunded(Boolean.TRUE.equals(data.get("refunded")));

        if (card != null) {
            dispute.setCardBin(asString(card.get("bin")));
            dispute.setCardLast4(asString(card.get("last4")));
        }

        if (pspTxn != null) {
            dispute.setPspTransactionId(asString(pspTxn.get("id")));
        }
    }

    private Alert createAlert(ChargebackDispute dispute) {
        Alert alert = new Alert();
        alert.setMerchantId(dispute.getMerchantId());
        alert.setAction("ALERT");
        alert.setStatus("open");
        alert.setSeverity(isFraudCategory(dispute) ? "CRITICAL" : "WARN");
        alert.setReason(buildAlertReason(dispute));
        alert.setScore(dispute.getCaseAmount() != null ? dispute.getCaseAmount().doubleValue() : 0.0);
        return alertRepository.save(alert);
    }

    private boolean shouldOpenCase(ChargebackDispute dispute) {
        return "accepted".equalsIgnoreCase(dispute.getRdrStatus())
                || isFraudCategory(dispute)
                || "DISPUTE_ALERT".equals(dispute.getNotificationType())
                || "PRE_DISPUTE".equals(dispute.getNotificationType());
    }

    private boolean isFraudCategory(ChargebackDispute dispute) {
        return "fraud".equalsIgnoreCase(dispute.getReasonCategory())
                || (dispute.getReasonCode() != null && dispute.getReasonCode().startsWith("10."));
    }

    private String buildAlertReason(ChargebackDispute dispute) {
        return "Verifi RDR " + dispute.getNotificationType()
                + (dispute.getRdrStatus() != null ? " [" + dispute.getRdrStatus() + "]" : "")
                + (dispute.getReasonCode() != null ? " reason=" + dispute.getReasonCode() : "");
    }

    private String buildCaseDescription(ChargebackDispute dispute) {
        return "Chargeback/RDR case from Verifi: type=" + dispute.getNotificationType()
                + ", status=" + dispute.getRdrStatus()
                + ", amount=" + dispute.getCaseAmount() + " " + dispute.getCaseCurrency()
                + ", ARN=" + dispute.getAcquirerReferenceNumber();
    }

    private Long resolveMerchantId(ChargebackDispute dispute) {
        if (dispute.getMerchantOrderId() != null) {
            try {
                return Long.parseLong(dispute.getMerchantOrderId().replaceAll("\\D", ""));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }

    private String resolveNotificationType(String webhookType, Map<String, Object> payload) {
        if (webhookType != null) {
            String normalized = webhookType.toLowerCase().replace('.', '_');
            if (normalized.contains("rdr") || normalized.contains("verifi")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = payload.get("data") instanceof Map
                        ? (Map<String, Object>) payload.get("data")
                        : payload;
                String status = asString(data.get("status"));
                if ("accepted".equalsIgnoreCase(status)) {
                    return "RDR_RESOLUTION";
                }
                if ("declined".equalsIgnoreCase(status)) {
                    return "RDR_DECLINED";
                }
                if (Boolean.TRUE.equals(data.get("refunded"))) {
                    return "RDR_PREVENTION";
                }
                return "DISPUTE_ALERT";
            }
        }
        String explicit = asString(payload.get("notification_type"));
        if (explicit != null) {
            return explicit.toUpperCase();
        }
        return "DISPUTE_ALERT";
    }

    private String categorizeReasonCode(String code) {
        if (code == null) {
            return null;
        }
        if (code.startsWith("10.")) {
            return "fraud";
        }
        if (code.startsWith("11.")) {
            return "authorization";
        }
        if (code.startsWith("12.")) {
            return "processing";
        }
        if (code.startsWith("13.")) {
            return "consumer";
        }
        return null;
    }

    private String firstHeader(Map<String, String> headers, String... names) {
        for (String name : names) {
            String value = headers.get(name);
            if (value == null) {
                value = headers.get(name.toLowerCase());
            }
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
