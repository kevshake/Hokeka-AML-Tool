package com.posgateway.aml.service.chargeback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.integration.verifi.VerifiRdrProperties;
import com.posgateway.aml.integration.verifi.VerifiRequestAuthenticator;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.service.case_management.CaseCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Processes Verifi API 3.0 notification callbacks and maps them to alerts, cases, and metrics.
 */
@Service
public class VerifiRdrWebhookService {

    private static final Logger log = LoggerFactory.getLogger(VerifiRdrWebhookService.class);

    private final VerifiRdrProperties properties;
    private final ChargebackDisputeRepository disputeRepository;
    private final AlertRepository alertRepository;
    private final MerchantRepository merchantRepository;
    private final CaseCreationService caseCreationService;
    private final ObjectMapper objectMapper;

    public VerifiRdrWebhookService(VerifiRdrProperties properties,
                                   ChargebackDisputeRepository disputeRepository,
                                   AlertRepository alertRepository,
                                   MerchantRepository merchantRepository,
                                   CaseCreationService caseCreationService,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.disputeRepository = disputeRepository;
        this.alertRepository = alertRepository;
        this.merchantRepository = merchantRepository;
        this.caseCreationService = caseCreationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChargebackDispute processWebhook(Map<String, String> headers, Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            log.warn("Verifi RDR webhook received but verifi.rdr.enabled=false — processing anyway for audit");
        }

        String dedupId = resolveDeduplicationId(headers, payload);
        if (dedupId != null) {
            Optional<ChargebackDispute> existing = disputeRepository.findByDeduplicationId(dedupId);
            if (existing.isPresent()) {
                log.info("Duplicate Verifi notification ignored: {}", dedupId);
                return existing.get();
            }
        }

        boolean verifiV3 = payload.containsKey("caseType");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = verifiV3
                ? payload
                : (payload.get("data") instanceof Map ? (Map<String, Object>) payload.get("data") : payload);

        String notificationType = resolveNotificationType(data, payload);
        String caseEvent = asString(data.get("caseEvent"));

        ChargebackDispute dispute = new ChargebackDispute();
        dispute.setExternalEventId(firstNonBlank(
                asString(data.get("caseId")),
                asString(payload.get("id"))));
        dispute.setDeduplicationId(dedupId);
        dispute.setNotificationType(notificationType);
        dispute.setScheme("visa");

        populateFromPayload(dispute, data, payload, verifiV3);

        Long merchantId = resolveMerchantId(dispute, data);
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

        Alert alert = createAlert(dispute, caseEvent);
        dispute.setAlertId(alert.getAlertId());

        if (properties.isAutoCreateCases() && shouldOpenCase(dispute, caseEvent)) {
            caseCreationService.triggerCaseFromChargeback(
                    dispute.getMerchantId(),
                    dispute.getPspId(),
                    notificationType,
                    buildCaseDescription(dispute));
        }

        return disputeRepository.save(dispute);
    }

    private String resolveDeduplicationId(Map<String, String> headers, Map<String, Object> payload) {
        String correlationId = VerifiRequestAuthenticator.correlationId(headers);
        if (correlationId != null) {
            return correlationId;
        }
        return firstNonBlank(
                VerifiRequestAuthenticator.firstHeader(headers,
                        "X-Butter-Webhook-Deduplication-ID",
                        "X-Verifi-Deduplication-ID",
                        "X-Webhook-Deduplication-ID"),
                asString(payload.get("caseId")));
    }

    private void populateFromPayload(ChargebackDispute dispute, Map<String, Object> data,
                                     Map<String, Object> root, boolean verifiV3) {
        if (verifiV3) {
            populateVerifiV3(dispute, data);
            return;
        }
        populateLegacyPartner(dispute, data, root);
    }

    private void populateVerifiV3(ChargebackDispute dispute, Map<String, Object> data) {
        dispute.setCaseId(asString(data.get("caseId")));
        dispute.setCaseDate(parseDate(data.get("caseDate")));
        dispute.setRdrStatus(firstNonBlank(
                asString(data.get("outcome")),
                mapCaseEventToStatus(asString(data.get("caseEvent")))));

        BigDecimal amount = amountFromObject(data.get("transactionAmount"));
        String currency = currencyFromObject(data.get("transactionAmount"));
        if (amount == null) {
            amount = amountFromObject(data.get("destinationAmount"));
            currency = firstNonBlank(currency, currencyFromObject(data.get("destinationAmount")));
        }
        dispute.setCaseAmount(amount);
        dispute.setCaseCurrency(currency);

        dispute.setAcquirerReferenceNumber(asString(data.get("arn")));
        dispute.setNetworkMerchantId(asString(data.get("cardAcceptorId")));
        dispute.setNetworkTransactionId(asString(data.get("transactionId")));
        dispute.setMerchantOrderId(asString(data.get("purchaseIdentifier")));
        dispute.setReasonCode(asString(data.get("reasonCode")));
        dispute.setCardBin(asString(data.get("cardBin")));
        dispute.setCardLast4(asString(data.get("cardLast4")));

        String outcome = asString(data.get("outcome"));
        if ("ACCEPTED".equalsIgnoreCase(outcome)) {
            dispute.setRefunded(true);
        }

        if (dispute.getReasonCode() != null) {
            dispute.setReasonCategory(categorizeReasonCode(dispute.getReasonCode()));
        }
    }

    @SuppressWarnings("unchecked")
    private void populateLegacyPartner(ChargebackDispute dispute, Map<String, Object> data,
                                       Map<String, Object> root) {
        Map<String, Object> visaRdr = data.get("visa_rdr") instanceof Map
                ? (Map<String, Object>) data.get("visa_rdr")
                : null;
        Map<String, Object> caseObj = data.get("case") instanceof Map
                ? (Map<String, Object>) data.get("case")
                : null;
        Map<String, Object> network = data.get("network") instanceof Map
                ? (Map<String, Object>) data.get("network")
                : null;
        Map<String, Object> card = data.get("card") instanceof Map
                ? (Map<String, Object>) data.get("card")
                : null;
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

    private Alert createAlert(ChargebackDispute dispute, String caseEvent) {
        Alert alert = new Alert();
        alert.setMerchantId(dispute.getMerchantId());
        alert.setAction("ALERT");
        alert.setStatus("open");
        alert.setSeverity(isFraudCategory(dispute) ? "CRITICAL" : "WARN");
        alert.setReason(buildAlertReason(dispute, caseEvent));
        alert.setScore(dispute.getCaseAmount() != null ? dispute.getCaseAmount().doubleValue() : 0.0);
        return alertRepository.save(alert);
    }

    private boolean shouldOpenCase(ChargebackDispute dispute, String caseEvent) {
        if ("DELETE".equalsIgnoreCase(caseEvent)) {
            return false;
        }
        return "ACCEPTED".equalsIgnoreCase(dispute.getRdrStatus())
                || isFraudCategory(dispute)
                || "FRAUD_NOTICE".equals(dispute.getNotificationType())
                || "DISPUTE_NOTICE".equals(dispute.getNotificationType())
                || "RDR_NOTICE".equals(dispute.getNotificationType());
    }

    private boolean isFraudCategory(ChargebackDispute dispute) {
        return "fraud".equalsIgnoreCase(dispute.getReasonCategory())
                || "FRAUD_NOTICE".equals(dispute.getNotificationType())
                || (dispute.getReasonCode() != null && dispute.getReasonCode().startsWith("10."));
    }

    private String buildAlertReason(ChargebackDispute dispute, String caseEvent) {
        return "Verifi " + dispute.getNotificationType()
                + (caseEvent != null ? " event=" + caseEvent : "")
                + (dispute.getRdrStatus() != null ? " [" + dispute.getRdrStatus() + "]" : "")
                + (dispute.getReasonCode() != null ? " reason=" + dispute.getReasonCode() : "");
    }

    private String buildCaseDescription(ChargebackDispute dispute) {
        return "Chargeback/RDR case from Verifi: type=" + dispute.getNotificationType()
                + ", status=" + dispute.getRdrStatus()
                + ", amount=" + dispute.getCaseAmount() + " " + dispute.getCaseCurrency()
                + ", ARN=" + dispute.getAcquirerReferenceNumber();
    }

    private Long resolveMerchantId(ChargebackDispute dispute, Map<String, Object> data) {
        if (dispute.getMerchantOrderId() != null) {
            try {
                return Long.parseLong(dispute.getMerchantOrderId().replaceAll("\\D", ""));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        String purchaseId = asString(data.get("purchaseIdentifier"));
        if (purchaseId != null) {
            try {
                return Long.parseLong(purchaseId.replaceAll("\\D", ""));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }

    private String resolveNotificationType(Map<String, Object> data, Map<String, Object> root) {
        String caseType = asString(data.get("caseType"));
        if (caseType != null) {
            return caseType.toUpperCase();
        }
        String explicit = asString(root.get("notification_type"));
        if (explicit != null) {
            return explicit.toUpperCase();
        }
        String webhookType = asString(root.get("type"));
        if (webhookType != null) {
            return webhookType.toUpperCase();
        }
        return "DISPUTE_NOTICE";
    }

    private String mapCaseEventToStatus(String caseEvent) {
        if (caseEvent == null) {
            return null;
        }
        return switch (caseEvent.toUpperCase()) {
            case "TIMEOUT" -> "TIMEOUT";
            case "DELETE" -> "DELETED";
            case "FAILED" -> "FAILED";
            default -> "PENDING";
        };
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

    @SuppressWarnings("unchecked")
    private BigDecimal amountFromObject(Object amountObj) {
        if (amountObj instanceof Map<?, ?> map) {
            return toBigDecimal(map.get("amount"));
        }
        return toBigDecimal(amountObj);
    }

    @SuppressWarnings("unchecked")
    private String currencyFromObject(Object amountObj) {
        if (amountObj instanceof Map<?, ?> map) {
            return asString(map.get("currency"));
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
        String text = value.toString();
        try {
            if (text.contains("T")) {
                return OffsetDateTime.parse(text).toLocalDate();
            }
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
