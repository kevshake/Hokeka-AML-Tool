package com.posgateway.aml.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.dto.TransactionResultRequest;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.TransactionDecision;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.util.Iso8583Utils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists gateway authorization and chargeback feedback against its source transaction. */
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/transaction/result")
public class TransactionResultController {

    private final TransactionRepository transactionRepository;
    private final PspIsolationService pspIsolationService;
    private final KafkaOutboxService outboxService;
    private final ObjectMapper objectMapper;

    public TransactionResultController(TransactionRepository transactionRepository,
                                       PspIsolationService pspIsolationService,
                                       KafkaOutboxService outboxService,
                                       ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.pspIsolationService = pspIsolationService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> ingestResult(@Valid @RequestBody TransactionResultRequest request) {
        TransactionEntity transaction = transactionRepository.findById(request.transactionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + request.transactionId()));
        pspIsolationService.validateTransactionAccess(transaction);
        validateSourceRecord(request, transaction);

        String responseCode = normalize(request.responseCode());
        if (responseCode != null) {
            transaction.setAcquirerResponse(responseCode);
        }

        TransactionDecision current = TransactionDecision.fromStored(transaction.getDecision());
        if (current == null) {
            current = TransactionDecision.fromRiskLevel(transaction.getRiskLevel());
        }
        TransactionDecision feedback = decisionFromFeedback(responseCode, Boolean.TRUE.equals(request.isChargeback()));
        TransactionDecision finalDecision = TransactionDecision.strongest(current, feedback);

        if (Boolean.TRUE.equals(request.isChargeback())) {
            transaction.setBillClassificationCode(normalizeClassification(request.billClassificationCode()));
        }
        transaction.setDecision(finalDecision.name());
        transaction.setRiskLevel(riskLevel(finalDecision));
        TransactionEntity saved = transactionRepository.save(transaction);
        enqueueAuditEvent(saved, responseCode, Boolean.TRUE.equals(request.isChargeback()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transactionId", saved.getTxnId());
        response.put("decision", saved.getDecision());
        response.put("riskLevel", saved.getRiskLevel());
        response.put("responseCode", saved.getAcquirerResponse());
        response.put("billClassificationCode", saved.getBillClassificationCode());
        response.put("updatedAt", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    private void validateSourceRecord(TransactionResultRequest request, TransactionEntity transaction) {
        if (request.merchantId() != null && !request.merchantId().isBlank()
                && !request.merchantId().equals(transaction.getMerchantId())) {
            throw new IllegalArgumentException("merchantId does not match the source transaction");
        }
        if (request.amountCents() != null && !request.amountCents().equals(transaction.getAmountCents())) {
            throw new IllegalArgumentException("amountCents does not match the source transaction");
        }
        if ((request.responseCode() == null || request.responseCode().isBlank())
                && !Boolean.TRUE.equals(request.isChargeback())) {
            throw new IllegalArgumentException("responseCode is required unless this is a chargeback event");
        }
    }

    private TransactionDecision decisionFromFeedback(String responseCode, boolean chargeback) {
        if (chargeback) {
            return TransactionDecision.BLOCK;
        }
        return switch (Iso8583Utils.getFraudSignal(responseCode)) {
            case CONFIRMED_FRAUD -> TransactionDecision.BLOCK;
            case HIGH_RISK -> TransactionDecision.HOLD;
            case VELOCITY -> TransactionDecision.ALERT;
            case NONE -> TransactionDecision.ALLOW;
        };
    }

    private String riskLevel(TransactionDecision decision) {
        return switch (decision) {
            case BLOCK -> "CRITICAL";
            case HOLD -> "HIGH";
            case ALERT -> "MEDIUM";
            case ALLOW -> "LOW";
        };
    }

    private String normalizeClassification(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "CHARGEBACK";
        }
        if (normalized.length() > 16) {
            throw new IllegalArgumentException("billClassificationCode must not exceed 16 characters");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void enqueueAuditEvent(TransactionEntity transaction, String responseCode, boolean chargeback) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "TRANSACTION_RESULT_RECORDED");
            event.put("transactionId", transaction.getTxnId());
            event.put("pspId", transaction.getPspId());
            event.put("merchantId", transaction.getMerchantId());
            event.put("responseCode", responseCode);
            event.put("chargeback", chargeback);
            event.put("decision", transaction.getDecision());
            event.put("riskLevel", transaction.getRiskLevel());
            event.put("billClassificationCode", transaction.getBillClassificationCode());
            event.put("timestamp", LocalDateTime.now().toString());

            String discriminator = responseCode != null ? responseCode : "NO_RESPONSE";
            outboxService.enqueue(
                    "transaction.result:" + transaction.getTxnId() + ":" + discriminator + ":" + chargeback,
                    KafkaConfig.TOPIC_TRANSACTIONS_AUDIT,
                    transaction.getPspId() != null ? String.valueOf(transaction.getPspId()) : "0",
                    objectMapper.writeValueAsString(event));
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Failed to enqueue transaction result audit for " + transaction.getTxnId(), failure);
        }
    }
}
