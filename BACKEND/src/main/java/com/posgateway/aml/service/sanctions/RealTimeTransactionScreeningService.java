package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time Transaction Screening Service
 * Screens transactions against sanctions lists at transaction processing time
 */
@Service
public class RealTimeTransactionScreeningService {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeTransactionScreeningService.class);

    private final AerospikeSanctionsScreeningService aerospikeScreeningService;
    private final MerchantRepository merchantRepository;
    private final ScreeningWhitelistService whitelistService;
    private final com.posgateway.aml.service.cache.ScreeningCacheService screeningCacheService; // Aerospike cache

    @Value("${screening.realtime.enabled:true}")
    private boolean realtimeScreeningEnabled;

    @Value("${screening.realtime.block-on-match:true}")
    private boolean blockOnMatch;

    @Value("${screening.realtime.screen-merchant:true}")
    private boolean screenMerchant;

    @Value("${screening.realtime.screen-counterparty:false}")
    private boolean screenCounterparty;

    @Autowired
    public RealTimeTransactionScreeningService(
            AerospikeSanctionsScreeningService aerospikeScreeningService,
            MerchantRepository merchantRepository,
            ScreeningWhitelistService whitelistService,
            com.posgateway.aml.service.cache.ScreeningCacheService screeningCacheService) {
        this.aerospikeScreeningService = aerospikeScreeningService;
        this.merchantRepository = merchantRepository;
        this.whitelistService = whitelistService;
        this.screeningCacheService = screeningCacheService;
    }

    /**
     * Screen transaction in real-time
     * 
     * @param transaction Transaction to screen
     * @return Screening result with matches and blocking recommendation
     */
    public TransactionScreeningResult screenTransaction(TransactionEntity transaction) {
        if (!realtimeScreeningEnabled) {
            logger.debug("Real-time screening disabled, skipping transaction {}", transaction.getTxnId());
            return TransactionScreeningResult.clear(transaction.getTxnId());
        }

        logger.debug("Screening transaction {} in real-time", transaction.getTxnId());

        List<ScreeningMatch> matches = new ArrayList<>();
        boolean shouldBlock = false;

        // Screen merchant if enabled
        if (screenMerchant && transaction.getMerchantId() != null) {
            try {
                // Try to find merchant by ID (merchantId in transaction is String, need to
                // convert)
                Long merchantIdLong = Long.parseLong(transaction.getMerchantId());
                Merchant merchant = merchantRepository.findById(merchantIdLong).orElse(null);
                if (merchant != null) {
                    ScreeningMatch merchantMatch = screenMerchant(merchant, transaction.getTxnId());
                    if (merchantMatch != null) {
                        matches.add(merchantMatch);
                        if (merchantMatch.isBlocking()) {
                            shouldBlock = true;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                logger.debug("Merchant ID {} is not a valid number, skipping merchant screening",
                        transaction.getMerchantId());
            }
        }

        // Screen counterparty if enabled (e.g., cardholder name from transaction)
        if (screenCounterparty) {
            // Extract counterparty information from transaction
            // This would need to be enhanced based on available transaction data
            String counterpartyName = extractCounterpartyName(transaction);
            if (counterpartyName != null && !counterpartyName.isEmpty()) {
                ScreeningMatch counterpartyMatch = screenCounterparty(counterpartyName, transaction.getTxnId());
                if (counterpartyMatch != null) {
                    matches.add(counterpartyMatch);
                    if (counterpartyMatch.isBlocking()) {
                        shouldBlock = true;
                    }
                }
            }
        }

        TransactionScreeningResult result = new TransactionScreeningResult(
                transaction.getTxnId(),
                matches,
                shouldBlock && blockOnMatch);

        if (result.hasMatches()) {
            logger.warn("Transaction {} screened: {} matches found, blocking={}",
                    transaction.getTxnId(), matches.size(), result.shouldBlock());
        }

        return result;
    }

    /**
     * Screen merchant against sanctions lists
     * Uses Aerospike cache for fast lookups
     */
    private ScreeningMatch screenMerchant(Merchant merchant, Long transactionId) {
        // Fast Aerospike cache lookup for whitelist
        if (screeningCacheService.isWhitelisted(merchant.getMerchantId(), "MERCHANT")) {
            logger.debug("Merchant {} is whitelisted (from cache), skipping screening", merchant.getMerchantId());
            return null;
        }

        // Check whitelist (fallback to DB)
        if (whitelistService.isWhitelisted(merchant.getMerchantId().toString(), "MERCHANT")) {
            logger.debug("Merchant {} is whitelisted, skipping screening", merchant.getMerchantId());
            screeningCacheService.cacheWhitelistEntry(merchant.getMerchantId(), merchant.getLegalName(), "MERCHANT");
            return null;
        }

        // Check Aerospike cache first for screening result
        ScreeningResult cachedResult = screeningCacheService.getCachedScreeningResult(
                String.valueOf(merchant.getMerchantId()), "MERCHANT");

        ScreeningResult legalNameResult;
        if (cachedResult != null) {
            logger.debug("Using cached screening result for merchant {}", merchant.getMerchantId());
            legalNameResult = cachedResult;
        } else {
            // Screen merchant legal name
            legalNameResult = aerospikeScreeningService.screenName(
                    merchant.getLegalName(),
                    ScreeningResult.EntityType.ORGANIZATION);
            // Cache the result for future lookups
            screeningCacheService.cacheScreeningResult(
                    String.valueOf(merchant.getMerchantId()), "MERCHANT", legalNameResult);
        }

        if (legalNameResult.hasMatches()) {
            return new ScreeningMatch(
                    merchant.getMerchantId().toString(),
                    "MERCHANT",
                    merchant.getLegalName(),
                    legalNameResult,
                    true // Blocking match
            );
        }

        // Screen trading name if different
        if (merchant.getTradingName() != null &&
                !merchant.getTradingName().equals(merchant.getLegalName())) {
            ScreeningResult tradingNameResult = aerospikeScreeningService.screenName(
                    merchant.getTradingName(),
                    ScreeningResult.EntityType.ORGANIZATION);

            if (tradingNameResult.hasMatches()) {
                return new ScreeningMatch(
                        merchant.getMerchantId().toString(),
                        "MERCHANT",
                        merchant.getTradingName(),
                        tradingNameResult,
                        true);
            }
        }

        return null; // No matches
    }

    /**
     * Screen counterparty (e.g., cardholder)
     * Uses Aerospike cache for fast lookups
     */
    private ScreeningMatch screenCounterparty(String name, Long transactionId) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        // Fast Aerospike cache lookup for whitelist
        // Note: For counterparty, we use name as key since we don't have a numeric ID
        // Check custom watchlist in Aerospike cache
        Boolean onCustomWatchlist = screeningCacheService.isOnCustomWatchlist(name, "PERSON");
        if (onCustomWatchlist != null && onCustomWatchlist) {
            logger.warn("Counterparty '{}' found on custom watchlist", name);
            return new ScreeningMatch(name, "COUNTERPARTY", name,
                    ScreeningResult.builder().status(ScreeningResult.ScreeningStatus.MATCH).build(), true);
        }

        // Check whitelist (fallback to DB)
        if (whitelistService.isWhitelisted(name, "COUNTERPARTY")) {
            logger.debug("Counterparty {} is whitelisted, skipping screening", name);
            return null;
        }

        ScreeningResult result = aerospikeScreeningService.screenName(
                name,
                ScreeningResult.EntityType.PERSON);

        if (result.hasMatches()) {
            return new ScreeningMatch(
                    name,
                    "COUNTERPARTY",
                    name,
                    result,
                    true);
        }

        return null;
    }

    /**
     * Extract cardholder / acceptor name from a transaction.
     *
     * <p>Priority:
     * <ol>
     *   <li>Custom "CH_NAME=foo|" tag (Hokeka pipe-encoded extension).</li>
     *   <li>ISO 8583 DE-43 (Card Acceptor Name & Location), TLV-encoded as
     *       {@code F43=...} or LLVAR-encoded inline. We extract the leading
     *       40 chars (acceptor name segment) per ISO 8583:1987/93.</li>
     *   <li>EMV tag 5F20 (Cardholder Name) parsed from {@code emvTags} JSON.</li>
     * </ol>
     * Returns {@code null} when nothing usable is present — callers MUST NOT
     * substitute a fabricated value.
     */
    private String extractCounterpartyName(TransactionEntity transaction) {
        String isoMsg = transaction.getIsoMsg();

        // 1. Hokeka pipe-encoded extension
        if (isoMsg != null && isoMsg.contains("CH_NAME=")) {
            try {
                int start = isoMsg.indexOf("CH_NAME=") + 8;
                int end = isoMsg.indexOf("|", start);
                if (end == -1) end = isoMsg.length();
                String v = isoMsg.substring(start, end).trim();
                if (!v.isEmpty()) return v;
            } catch (Exception e) {
                logger.warn("Failed to extract CH_NAME: {}", e.getMessage());
            }
        }

        // 2. ISO 8583 DE-43 — accept "F43=" prefix or full 40-char acceptor segment.
        if (isoMsg != null && isoMsg.contains("F43=")) {
            try {
                int start = isoMsg.indexOf("F43=") + 4;
                int end = isoMsg.indexOf("|", start);
                if (end == -1) end = Math.min(isoMsg.length(), start + 40);
                String acceptor = isoMsg.substring(start, end).trim();
                // DE-43 layout: <acceptor name 22 / 25><city 13><state 3 / country 3>.
                // Leading 22-25 chars contain the acceptor / cardholder name.
                if (acceptor.length() > 25) acceptor = acceptor.substring(0, 25);
                acceptor = acceptor.trim();
                if (!acceptor.isEmpty()) return acceptor;
            } catch (Exception e) {
                logger.warn("Failed to extract DE-43: {}", e.getMessage());
            }
        }

        // 3. EMV tag 5F20 (Cardholder Name) from the parsed EMV-tag JSON map.
        try {
            String emvJson = transaction.getEmvTags();
            if (emvJson != null && emvJson.contains("5F20")) {
                com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> tags = m.readValue(emvJson, java.util.Map.class);
                Object v = tags.get("5F20");
                if (v == null) v = tags.get("5f20");
                if (v != null) {
                    String s = v.toString().trim();
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Exception e) {
            logger.debug("EMV 5F20 extraction failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Transaction Screening Result
     */
    public static class TransactionScreeningResult {
        private final Long transactionId;
        private final List<ScreeningMatch> matches;
        private final boolean shouldBlock;

        public TransactionScreeningResult(Long transactionId, List<ScreeningMatch> matches, boolean shouldBlock) {
            this.transactionId = transactionId;
            this.matches = matches != null ? matches : new ArrayList<>();
            this.shouldBlock = shouldBlock;
        }

        public static TransactionScreeningResult clear(Long transactionId) {
            return new TransactionScreeningResult(transactionId, new ArrayList<>(), false);
        }

        public boolean hasMatches() {
            return !matches.isEmpty();
        }

        public Long getTransactionId() {
            return transactionId;
        }

        public List<ScreeningMatch> getMatches() {
            return matches;
        }

        public boolean shouldBlock() {
            return shouldBlock;
        }
    }

    /**
     * Screening Match
     */
    public static class ScreeningMatch {
        private final String entityId;
        private final String entityType;
        private final String screenedName;
        private final ScreeningResult screeningResult;
        private final boolean blocking;

        public ScreeningMatch(String entityId, String entityType, String screenedName,
                ScreeningResult screeningResult, boolean blocking) {
            this.entityId = entityId;
            this.entityType = entityType;
            this.screenedName = screenedName;
            this.screeningResult = screeningResult;
            this.blocking = blocking;
        }

        public String getEntityId() {
            return entityId;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getScreenedName() {
            return screenedName;
        }

        public ScreeningResult getScreeningResult() {
            return screeningResult;
        }

        public boolean isBlocking() {
            return blocking;
        }
    }
}
