package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.enrichment.BinLookupService;
import com.posgateway.aml.service.enrichment.IpGeoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Transaction Ingestion Service
 * Receives and stores transactions from all merchants
 * Tracks every transaction for AML and fraud analysis
 */
@Service
public class TransactionIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionIngestionService.class);

    private final TransactionRepository transactionRepository;
    private final com.posgateway.aml.repository.MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    private final com.posgateway.aml.service.kafka.KafkaOutboxService kafkaOutboxService;

    private final TransactionStatisticsService statisticsService;
    private final com.posgateway.aml.service.risk.RiskScoringService riskScoringService;
    private final IpGeoService ipGeoService;
    private final BinLookupService binLookupService;
    private final com.posgateway.aml.service.security.PiiLookupHasher piiLookupHasher;

    @Autowired
    public TransactionIngestionService(TransactionRepository transactionRepository,
            com.posgateway.aml.repository.MerchantRepository merchantRepository,
            ObjectMapper objectMapper,
            com.posgateway.aml.service.kafka.KafkaOutboxService kafkaOutboxService,
            TransactionStatisticsService statisticsService,
            com.posgateway.aml.service.risk.RiskScoringService riskScoringService,
            IpGeoService ipGeoService,
            BinLookupService binLookupService,
            com.posgateway.aml.service.security.PiiLookupHasher piiLookupHasher) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
        this.kafkaOutboxService = kafkaOutboxService;
        this.statisticsService = statisticsService;
        this.riskScoringService = riskScoringService;
        this.ipGeoService = ipGeoService;
        this.binLookupService = binLookupService;
        this.piiLookupHasher = piiLookupHasher;
    }

    /**
     * Ingest transaction from merchant
     * 
     * @param transactionRequest Transaction data from merchant
     * @return Saved transaction entity
     */
    @Transactional
    public TransactionEntity ingestTransaction(TransactionRequest transactionRequest) {
        validateRequest(transactionRequest);
        logger.info("Ingesting transaction from merchant: {}", transactionRequest.getMerchantId());

        TransactionEntity transaction = new TransactionEntity();

        // Store raw ISO message if provided
        transaction.setIsoMsg(transactionRequest.getIsoMsg());

        // Hash PAN for privacy (tokenize)
        if (transactionRequest.getPan() != null && !transactionRequest.getPan().isEmpty()) {
            transaction.setPanHash(hashPan(transactionRequest.getPan()));
        }

        transaction.setMerchantId(transactionRequest.getMerchantId());

        Long merchantId = parseMerchantId(transactionRequest.getMerchantId());
        com.posgateway.aml.entity.merchant.Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown merchantId: " + transactionRequest.getMerchantId()));
        if (merchant.getPsp() != null) {
            transaction.setPspId(merchant.getPsp().getPspId());
        }
        Double krs = riskScoringService.calculateKrs(merchant);
        transaction.setKrs(krs);

        String originCountry = deriveOriginCountry(transactionRequest);
        java.math.BigDecimal amountUnits = java.math.BigDecimal
                .valueOf(transactionRequest.getAmountCents(), 2);
        Double trs = riskScoringService.calculateTrs(originCountry, merchant.getCountry(), amountUnits);
        transaction.setTrs(trs);

        Double newCra = riskScoringService.updateCra(merchant.getCra(), trs);
        transaction.setCra(newCra);
        merchant.setCra(newCra);
        merchant.setKrs(krs);
        merchantRepository.save(merchant);
        transaction.setMerchantCountry(merchant.getCountry());

        transaction.setTerminalId(transactionRequest.getTerminalId());
        transaction.setChannelType(transactionRequest.getChannelType());
        transaction.setCashTransaction(transactionRequest.isCashTransaction());
        transaction.setCustomerAccountReference(transactionRequest.getCustomerAccountReference());
        transaction.setCustomerEmail(transactionRequest.getCustomerEmail());
        transaction.setAmountCents(transactionRequest.getAmountCents());
        transaction.setCurrency(transactionRequest.getCurrency().toUpperCase());
        transaction.setIpAddress(transactionRequest.getIpAddress());
        if (transactionRequest.getCountryCode() != null && !transactionRequest.getCountryCode().isBlank()) {
            transaction.setMerchantCountry(transactionRequest.getCountryCode().toUpperCase());
        }
        transaction
                .setTxnTs(transactionRequest.getTxnTs() != null ? transactionRequest.getTxnTs() : LocalDateTime.now());

        // Store EMV tags as JSON
        if (transactionRequest.getEmvTags() != null && !transactionRequest.getEmvTags().isEmpty()) {
            try {
                String emvJson = objectMapper.writeValueAsString(transactionRequest.getEmvTags());
                transaction.setEmvTags(emvJson);
            } catch (Exception e) {
                logger.warn("Failed to serialize EMV tags to JSON", e);
            }
        }

        transaction.setAcquirerResponse(transactionRequest.getAcquirerResponse());
        transaction.setDirection(transactionRequest.getDirection()); // Store Direction

        // Calculate and store riskLevel and decision for pagination performance
        String riskLevel = calculateRiskLevel(transaction);
        String decision = calculateDecision(riskLevel);
        transaction.setRiskLevel(riskLevel);
        transaction.setDecision(decision);

        TransactionEntity saved = transactionRepository.save(transaction);

        // The transaction and event commit atomically; the dispatcher publishes later.
        publishRawTransactionEvent(saved);

        // Automatically record transaction statistics for AML velocity checks
        statisticsService.recordTransaction(
                saved.getMerchantId(),
                saved.getPanHash(),
                saved.getAmountCents(),
                saved.getTerminalId());

        logger.info("Transaction ingested successfully: txnId={}, merchantId={}, riskLevel={}, decision={}",
                saved.getTxnId(), saved.getMerchantId(), riskLevel, decision);

        return saved;
    }

    /**
     * Publish a minimal raw-transaction event to {@code transactions.raw}.
     * Keyed by pspId so consumers can partition-route by tenant.
     * Enqueues atomically with the transaction; failures roll back the database transaction.
     */
    private void publishRawTransactionEvent(TransactionEntity saved) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("transactionId", saved.getTxnId());
            event.put("pspId", saved.getPspId());
            event.put("merchantId", saved.getMerchantId());
            event.put("amountCents", saved.getAmountCents());
            event.put("currencyCode", saved.getCurrency());
            event.put("transactionTimestamp", saved.getTxnTs() != null ? saved.getTxnTs().toString() : null);
            event.put("channelType", saved.getChannelType());
            event.put("cashTransaction", saved.isCashTransaction());
            event.put("panHash", saved.getPanHash());
            event.put("riskLevel", saved.getRiskLevel());
            event.put("decision", saved.getDecision());

            String payload = objectMapper.writeValueAsString(event);
            String partitionKey = saved.getPspId() != null ? String.valueOf(saved.getPspId()) : "0";
            kafkaOutboxService.enqueue(
                    "transaction.raw:" + saved.getTxnId(),
                    KafkaConfig.TOPIC_TRANSACTIONS_RAW,
                    partitionKey,
                    payload);
            logger.debug("Queued raw transaction event: txnId={}", saved.getTxnId());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to serialize or enqueue transaction event for txnId=" + saved.getTxnId(), e);
        }
    }

    private Long parseMerchantId(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("merchantId is required");
        }
        try {
            return Long.valueOf(merchantId);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("merchantId must be a numeric platform merchant ID", invalid);
        }
    }

    private void validateRequest(TransactionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("transaction request is required");
        }
        parseMerchantId(request.getMerchantId());
        if (request.getAmountCents() == null || request.getAmountCents() <= 0) {
            throw new IllegalArgumentException("amountCents must be greater than zero");
        }
        if (request.getCurrency() == null || !request.getCurrency().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter ISO 4217 code");
        }
    }

    /**
     * Calculate risk level based on TRS score
     */
    private String calculateRiskLevel(TransactionEntity txn) {
        int score = getRiskScore(txn);
        if (score >= 76) return "CRITICAL";
        if (score >= 51) return "HIGH";
        if (score >= 26) return "MEDIUM";
        return "LOW";
    }

    /**
     * Calculate decision based on risk level
     */
    private String calculateDecision(String riskLevel) {
        return com.posgateway.aml.model.TransactionDecision.fromRiskLevel(riskLevel).name();
    }

    /**
     * Get risk score from TRS or fallback to amount-based calculation
     */
    private int getRiskScore(TransactionEntity txn) {
        // Use Real TRS (Transaction Risk Score) if available
        if (txn.getTrs() != null) {
            return txn.getTrs().intValue();
        }
        // Fallback for old data or if scoring failed
        if (txn.getAmountCents() != null && txn.getAmountCents() > 100000) return 95;
        if (txn.getAmountCents() != null && txn.getAmountCents() > 50000) return 75;
        return 25;
    }

    /**
     * Derive transaction origin country from IP first, then BIN.
     *
     * <p>Returns {@code "UNKNOWN"} only if every available signal fails — but
     * unlike the previous placeholder, the actual cache and DB are consulted.
     */
    private String deriveOriginCountry(TransactionRequest req) {
        if (req == null) return "UNKNOWN";
        Optional<String> byIp = ipGeoService.lookupCountry(req.getIpAddress());
        if (byIp.isPresent()) return byIp.get();
        // Fall back to BIN of card.
        String pan = req.getPan();
        if (pan != null && pan.length() >= 6) {
            Optional<String> byBin = binLookupService.lookupCountry(pan.substring(0, Math.min(8, pan.length())));
            if (byBin.isPresent()) return byBin.get();
        }
        return "UNKNOWN";
    }

    /**
     * Produce the "comparable ciphered" card fingerprint stored as {@code pan_hash}.
     *
     * <p>Preferred: a <b>keyed HMAC-SHA256</b> ({@link com.posgateway.aml.service.security.PiiLookupHasher}).
     * It is deterministic — the same card always maps to the same value, which is what makes velocity
     * checks and the Do-Not-Honour card match work — but because it is keyed, a leaked database cannot
     * be brute-forced back to PANs the way an unkeyed SHA-256 of a low-entropy PAN can.
     *
     * <p>Fallback (dev/test only): if {@code PII_LOOKUP_HMAC_KEY} is not configured we use a plain
     * SHA-256 digest so local ingestion still works. Production <b>requires</b> the key (enforced at
     * startup by {@code EnvVarStartupValidator}), so the secure HMAC path is always used there.
     */
    private String hashPan(String pan) {
        if (piiLookupHasher.isConfigured()) {
            return piiLookupHasher.hashIdentifier(pan);
        }
        logger.warn("PII_LOOKUP_HMAC_KEY not configured — using unkeyed PAN digest (dev/test only; "
                + "production enforces the HMAC key)");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pan.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("PAN hashing failed; refusing to persist raw PAN", e);
        }
    }

    /**
     * Transaction Request DTO
     */
    public static class TransactionRequest {
        private String isoMsg;
        private String pan;
        private String merchantId;
        private String terminalId;
        private Long amountCents;
        private String currency;
        private LocalDateTime txnTs;
        private Map<String, Object> emvTags;
        private String acquirerResponse;
        private String direction;
        private String ipAddress;
        private String countryCode;
        private String channelType;
        private boolean cashTransaction;
        private String customerAccountReference;
        private String customerEmail;

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        // Getters and Setters
        public String getIsoMsg() {
            return isoMsg;
        }

        public void setIsoMsg(String isoMsg) {
            this.isoMsg = isoMsg;
        }

        public String getPan() {
            return pan;
        }

        public void setPan(String pan) {
            this.pan = pan;
        }

        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public String getTerminalId() {
            return terminalId;
        }

        public void setTerminalId(String terminalId) {
            this.terminalId = terminalId;
        }

        public Long getAmountCents() {
            return amountCents;
        }

        public void setAmountCents(Long amountCents) {
            this.amountCents = amountCents;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public LocalDateTime getTxnTs() {
            return txnTs;
        }

        public void setTxnTs(LocalDateTime txnTs) {
            this.txnTs = txnTs;
        }

        public Map<String, Object> getEmvTags() {
            return emvTags;
        }

        public void setEmvTags(Map<String, Object> emvTags) {
            this.emvTags = emvTags;
        }

        public String getAcquirerResponse() {
            return acquirerResponse;
        }

        public void setAcquirerResponse(String acquirerResponse) {
            this.acquirerResponse = acquirerResponse;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }

        public String getChannelType() {
            return channelType;
        }

        public void setChannelType(String channelType) {
            this.channelType = channelType;
        }

        public boolean isCashTransaction() {
            return cashTransaction;
        }

        public void setCashTransaction(boolean cashTransaction) {
            this.cashTransaction = cashTransaction;
        }

        public String getCustomerAccountReference() {
            return customerAccountReference;
        }

        public void setCustomerAccountReference(String customerAccountReference) {
            this.customerAccountReference = customerAccountReference;
        }

        public String getCustomerEmail() {
            return customerEmail;
        }

        public void setCustomerEmail(String customerEmail) {
            this.customerEmail = customerEmail;
        }
    }
}
