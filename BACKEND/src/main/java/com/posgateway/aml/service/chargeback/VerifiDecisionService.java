package com.posgateway.aml.service.chargeback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import com.posgateway.aml.entity.chargeback.VerifiDecision;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.repository.chargeback.VerifiDecisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Synchronous Verifi API 3.0 Decision endpoint — accept or decline pre-disputes within the 2-second SLA.
 */
@Service
public class VerifiDecisionService {

    private static final Logger log = LoggerFactory.getLogger(VerifiDecisionService.class);

    private final VerifiDecisionRepository decisionRepository;
    private final ChargebackDisputeRepository disputeRepository;
    private final ObjectMapper objectMapper;

    public VerifiDecisionService(VerifiDecisionRepository decisionRepository,
                                 ChargebackDisputeRepository disputeRepository,
                                 ObjectMapper objectMapper) {
        this.decisionRepository = decisionRepository;
        this.disputeRepository = disputeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> evaluateDecision(Map<String, Object> request) {
        long started = System.currentTimeMillis();

        String decisionId = firstNonBlank(
                asString(request.get("decisionId")),
                asString(request.get("case_id")),
                asString(request.get("caseId")));
        String pspTxnId = firstNonBlank(
                asString(request.get("transactionId")),
                asString(request.get("psp_transaction_id")));

        String outcome = "DECLINED";
        String statusCode = "957";
        String reason = "Default decline — no matching RDR rule";

        Optional<ChargebackDispute> disputeOpt = Optional.empty();
        if (decisionId != null) {
            disputeOpt = disputeRepository.findTopByCaseIdOrderByCreatedAtDesc(decisionId);
        }

        BigDecimal caseAmount = extractCaseAmount(request);
        String reasonCode = extractReasonCode(request, disputeOpt.orElse(null));

        if (caseAmount != null && caseAmount.compareTo(BigDecimal.valueOf(500)) < 0
                && !isFraudReason(reasonCode)) {
            outcome = "ACCEPTED";
            statusCode = "103";
            reason = "Low-value non-fraud dispute — auto-accept liability";
        } else if (isFraudReason(reasonCode)) {
            outcome = "DECLINED";
            statusCode = "957";
            reason = "Fraud-category dispute — decline RDR prevention";
        } else if (disputeOpt.isPresent() && "ACCEPTED".equalsIgnoreCase(disputeOpt.get().getRdrStatus())) {
            outcome = "ACCEPTED";
            statusCode = "103";
            reason = "Prior RDR notification marked ACCEPTED";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("outcome", outcome);
        response.put("statusCode", statusCode);
        response.put("reason", reason);
        if ("ACCEPTED".equals(outcome) && caseAmount != null) {
            Map<String, Object> refundAmount = new LinkedHashMap<>();
            refundAmount.put("amount", caseAmount);
            refundAmount.put("currency", extractCurrency(request));
            response.put("refundAmount", refundAmount);
        }

        VerifiDecision audit = new VerifiDecision();
        audit.setCaseId(decisionId);
        audit.setPspTransactionId(pspTxnId);
        audit.setDecision(outcome);
        audit.setReason(reason);
        audit.setLatencyMs((int) (System.currentTimeMillis() - started));
        disputeOpt.map(ChargebackDispute::getMerchantId).ifPresent(audit::setMerchantId);
        try {
            audit.setRequestPayload(objectMapper.writeValueAsString(request));
            audit.setResponsePayload(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            audit.setRequestPayload(String.valueOf(request));
            audit.setResponsePayload(String.valueOf(response));
        }
        decisionRepository.save(audit);

        log.info("Verifi decision decisionId={} outcome={} latencyMs={}", decisionId, outcome, audit.getLatencyMs());
        return response;
    }

    private boolean isFraudReason(String reasonCode) {
        if (reasonCode == null) {
            return false;
        }
        return reasonCode.startsWith("10.") || "fraud".equalsIgnoreCase(reasonCode);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractCaseAmount(Map<String, Object> request) {
        Object caseAmount = request.get("caseAmount");
        if (caseAmount instanceof Map<?, ?> map) {
            Object amt = map.get("amount");
            if (amt instanceof Number) {
                return BigDecimal.valueOf(((Number) amt).doubleValue());
            }
        }
        Object txnAmount = request.get("transactionAmount");
        if (txnAmount instanceof Map<?, ?> map) {
            Object amt = map.get("amount");
            if (amt instanceof Number) {
                return BigDecimal.valueOf(((Number) amt).doubleValue());
            }
        }
        Object flat = request.get("amount");
        if (flat instanceof Number) {
            return BigDecimal.valueOf(((Number) flat).doubleValue());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractCurrency(Map<String, Object> request) {
        Object caseAmount = request.get("caseAmount");
        if (caseAmount instanceof Map<?, ?> map) {
            String currency = asString(map.get("currency"));
            if (currency != null) {
                return currency;
            }
        }
        Object txnAmount = request.get("transactionAmount");
        if (txnAmount instanceof Map<?, ?> map) {
            return asString(map.get("currency"));
        }
        return "USD";
    }

    private String extractReasonCode(Map<String, Object> request, ChargebackDispute dispute) {
        String code = asString(request.get("reasonCode"));
        if (code == null) {
            code = asString(request.get("reason_code"));
        }
        if (code == null && dispute != null) {
            code = dispute.getReasonCode();
        }
        return code;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
