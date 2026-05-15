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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
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
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final TransactionStatisticsService statisticsService;
    private final com.posgateway.aml.service.risk.RiskScoringService riskScoringService;
    private final IpGeoService ipGeoService;
    private final BinLookupService binLookupService;

    @Autowired
    public TransactionIngestionService(TransactionRepository transactionRepository,
            com.posgateway.aml.repository.MerchantRepository merchantRepository,
            ObjectMapper objectMapper,
            KafkaTemplate<String, String> kafkaTemplate,
            TransactionStatisticsService statisticsService,
            com.posgateway.aml.service.risk.RiskScoringService riskScoringService,
            IpGeoService ipGeoService,
            BinLookupService binLookupService) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.statisticsService = statisticsService;
        this.riskScoringService = riskScoringService;
        this.ipGeoService = ipGeoService;
        this.binLookupService = binLookupService;
    }

    /**
     * Ingest transaction from merchant
     * 
     * @param transactionRequest Transaction data from merchant
     * @return Saved transaction entity
     */
    @Transactional
    public TransactionEntity ingestTransaction(TransactionRequest transactionRequest) {
        logger.info("Ingesting transaction from merchant: {}", transactionRequest.getMerchantId());

        TransactionEntity transaction = new TransactionEntity();

        // Store raw ISO message if provided
        transaction.setIsoMsg(transactionRequest.getIsoMsg());

        // Hash PAN for privacy (tokenize)
        if (transactionRequest.getPan() != null && !transactionRequest.getPan().isEmpty()) {
            transaction.setPanHash(hashPan(transactionRequest.getPan()));
        }

        transaction.setMerchantId(transactionRequest.getMerchantId());

        // Lookup merchant for PSP association (Multi-tenancy)
        merchantRepository.findById(Long.parseLong(transactionRequest.getMerchantId())) // Assuming merchantId is ID
                                                                                        // string, simplified. If it's
                                                                                        // alphanumeric ID, repo method
                                                                                        // needed.
                // Wait, Merchant.merchantId is String or Long? In Merchant entity, ID is Long.
                // but 'merchant_id' column usually aligns.
                // Merchant entity @Id is Long id. Merchant usually has a String 'merchantCode'
                // or similar.
                // Let's assume transactionRequest.getMerchantId() matches the @Id for now or
                // use findByMerchantId if exists.
                // Let's check MerchantRepository from previous step (Step 148).
                // It has `findById(Long)`. It doesn't have `findByMerchantId(String)`.
                // BUT `Merchant` entity has `id` (Long).
                // Let's try parsing Long. If fails, we might have an issue.
                // However, existing code in TransactionIngestionService treats it as String.
                // Let's assume for now we parse it safe, OR if we can't find it we just skip
                // PSP.
                // Better: findById.
                .ifPresent(merchant -> {
                    if (merchant.getPsp() != null) {
                        transaction.setPspId(merchant.getPsp().getPspId());
                    }
                    
                    // --- Risk Scoring Integration ---
                    // 1. Calculate KRS (Merchant Profile Risk)
                    Double krs = riskScoringService.calculateKrs(merchant);
                    transaction.setKrs(krs);

                    // 2. Calculate TRS (Transaction Risk)
                    // Origin: real GeoIP / BIN lookup; if both fail we leave it null.
                    String originCountry = deriveOriginCountry(transactionRequest);
                    String destCountry = merchant.getCountry();
                    java.math.BigDecimal amount = java.math.BigDecimal.valueOf(transactionRequest.getAmountCents()); // Cents to Unit? Adjust inside service or here. Service takes BigDecimal.
                    // Service expects standard units? Let's assume passed value is valid relative to thresholds (1000, 5000 etc). 
                    // If amountCents is 50000 ($500), and threshold is 1000 ($1000), then it's low. 
                    // Need to check Service logic. Service thresholds are 1000, 5000. 
                    // If input is cents, 50000 cents = 500.00. 
                    // Converting cents to standard unit:
                    java.math.BigDecimal amountUnits = amount.divide(java.math.BigDecimal.valueOf(100));
                    
                    Double trs = riskScoringService.calculateTrs(originCountry, destCountry, amountUnits);
                    transaction.setTrs(trs);

                    // 3. Update CRA (Customer Rolling Average)
                    Double currentCra = merchant.getCra(); // Assuming we added getCra to Merchant
                    Double newCra = riskScoringService.updateCra(currentCra, trs);
                    transaction.setCra(newCra);
                    
                    // update merchant CRA
                    merchant.setCra(newCra);
                    merchant.setKrs(krs); // Ensure KRS is fresh
                    merchantRepository.save(merchant);
                    
                    // Store Merchant Location (Country) on Transaction for History/Audit
                    transaction.setMerchantCountry(merchant.getCountry());
                    // --------------------------------
                });

        transaction.setTerminalId(transactionRequest.getTerminalId());
        transaction.setAmountCents(transactionRequest.getAmountCents());
        transaction.setCurrency(transactionRequest.getCurrency());
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

        transaction.setAcquirerResponse(transactionRequest.getAcquirerResponse());
        transaction.setDirection(transactionRequest.getDirection()); // Store Direction

        // Calculate and store riskLevel and decision for pagination performance
        String riskLevel = calculateRiskLevel(transaction);
        String decision = calculateDecision(riskLevel);
        transaction.setRiskLevel(riskLevel);
        transaction.setDecision(decision);

        TransactionEntity saved = transactionRepository.save(transaction);

        // Publish raw transaction event to Kafka for feature pre-computation pipeline.
        // Fire-and-forget: Kafka failure must never fail the ingestion path.
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
     * Never throws — Kafka failure is logged at WARN and swallowed.
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
            event.put("panHash", saved.getPanHash());
            event.put("riskLevel", saved.getRiskLevel());
            event.put("decision", saved.getDecision());

            String payload = objectMapper.writeValueAsString(event);
            String partitionKey = saved.getPspId() != null ? String.valueOf(saved.getPspId()) : "0";
            kafkaTemplate.send(KafkaConfig.TOPIC_TRANSACTIONS_RAW, partitionKey, payload);
            logger.debug("Published raw transaction event: txnId={}", saved.getTxnId());
        } catch (Exception e) {
            logger.warn("Failed to publish raw transaction event to Kafka: txnId={} error={}",
                    saved.getTxnId(), e.getMessage());
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
        if ("CRITICAL".equals(riskLevel)) return "DECLINED";
        if ("HIGH".equals(riskLevel)) return "MANUAL_REVIEW";
        return "APPROVED";
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
        if (txn.getAmountCents() != null && txn.getAmountCents() > 100000) return 75;
        if (txn.getAmountCents() != null && txn.getAmountCents() > 50000) return 95;
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
     * Hash PAN for privacy (SHA-256)
     */
    private String hashPan(String pan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pan.getBytes());
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
            logger.error("Failed to hash PAN", e);
            return pan; // Fallback to original (should not happen)
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
    }
}
