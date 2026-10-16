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

    @Value("${screening.realtime.block-on-unavailable:true}")
    private boolean blockOnUnavailable;

    @Value("${screening.realtime.screen-merchant:true}")
    private boolean screenMerchant;

    @Value("${screening.realtime.screen-counterparty:true}")
    private boolean screenCounterparty;

    /**
     * Similarity at or above which a match is treated as a confirmed hit even when the provider
     * labelled it POTENTIAL_MATCH. Mirrors the sanctions microservice FLAGGED threshold (0.95).
     */
    @Value("${screening.realtime.confirmed-match-threshold:0.95}")
    private double confirmedMatchThreshold = 0.95;

    @Autowired(required = false)
    private com.posgateway.aml.service.ai.decision.AiEngineAdvisor aiEngineAdvisor;

    @Autowired(required = false)
    private ScreeningActivityRecorder screeningActivityRecorder;

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
        boolean screeningUnavailable = false;

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
            } catch (ScreeningUnavailableException e) {
                screeningUnavailable = true;
                logger.error("Merchant sanctions screening unavailable for transaction {}", transaction.getTxnId());
            }
        }

        // Screen counterparty if enabled (e.g., cardholder name from transaction)
        if (screenCounterparty) {
            // Extract counterparty information from transaction
            // This would need to be enhanced based on available transaction data
            String counterpartyName = extractCounterpartyName(transaction);
            if (counterpartyName != null && !counterpartyName.isEmpty()) {
                try {
                    ScreeningMatch counterpartyMatch = screenCounterparty(counterpartyName, transaction.getTxnId());
                    if (counterpartyMatch != null) {
                        matches.add(counterpartyMatch);
                        if (counterpartyMatch.isBlocking()) {
                            shouldBlock = true;
                        }
                    }
                } catch (ScreeningUnavailableException e) {
                    screeningUnavailable = true;
                    logger.error("Counterparty sanctions screening unavailable for transaction {}", transaction.getTxnId());
                }
            }
        }

        TransactionScreeningResult result = new TransactionScreeningResult(
                transaction.getTxnId(),
                matches,
                (shouldBlock && blockOnMatch) || (screeningUnavailable && blockOnUnavailable),
                screeningUnavailable);

        if (result.hasMatches()) {
            logger.warn("Transaction {} screened: {} matches found, blocking={}",
                    transaction.getTxnId(), matches.size(), result.shouldBlock());
            if (aiEngineAdvisor != null) {
                ScreeningMatch primary = matches.get(0);
                java.util.Map<String, Object> features = buildSanctionsAiFeatures(primary, matches.size());
                String baseline = result.shouldBlock() ? "BLOCK" : "REVIEW";
                aiEngineAdvisor.adviseAsync(
                        com.posgateway.aml.service.ai.decision.AiEngineType.SANCTIONS_DISAMBIGUATION,
                        transaction.getPspId(),
                        baseline,
                        features,
                        transaction.getTxnId(),
                        null,
                        null,
                        null);
            }
        }

        if (screeningActivityRecorder != null) {
            String subjectName = matches.isEmpty() ? null : matches.get(0).getScreenedName();
            String subjectEntityType = matches.isEmpty() ? null : matches.get(0).getEntityType();
            screeningActivityRecorder.recordTransactionScreening(transaction, result, "TRANSACTION", subjectName,
                    subjectEntityType);
        }

        return result;
    }

    /**
     * Decides whether a screening result with matches is a confirmed (blocking) hit or a
     * non-exact candidate that must go to disambiguation / manual review.
     *
     * <p>Confirmed, and therefore blocking: provider status {@code MATCH} (the microservice's
     * FLAGGED, similarity &gt;= 0.95), any match whose similarity reaches
     * {@link #confirmedMatchThreshold}, a DOB-confirmed match, or a result whose status is missing
     * or unrecognised (fail closed). Only an explicit {@code POTENTIAL_MATCH} below the threshold
     * is non-blocking; those transactions are still held for review by the decision engine, never
     * allowed through. Previously every match was hard-coded as blocking, so the disambiguation
     * baseline was always BLOCK and fuzzy candidates could never be routed to review.
     */
    boolean isConfirmedHit(ScreeningResult result) {
        if (result == null || !result.hasMatches()) {
            return false;
        }
        if (result.getStatus() != ScreeningResult.ScreeningStatus.POTENTIAL_MATCH) {
            return true;
        }
        if (result.getHighestMatchScore() != null && result.getHighestMatchScore() >= confirmedMatchThreshold) {
            return true;
        }
        for (ScreeningResult.Match match : result.getMatches()) {
            if (match.getMatchType() == ScreeningResult.MatchType.DOB_CONFIRMED) {
                return true;
            }
            if (match.getSimilarityScore() != null && match.getSimilarityScore() >= confirmedMatchThreshold) {
                return true;
            }
        }
        return false;
    }

    private static java.util.Map<String, Object> buildSanctionsAiFeatures(ScreeningMatch primary, int matchCount) {
        java.util.Map<String, Object> features = new java.util.HashMap<>();
        features.put("match_count", matchCount);
        features.put("screened_entity_type", primary.getEntityType());
        com.posgateway.aml.model.ScreeningResult sr = primary.getScreeningResult();
        if (sr != null) {
            features.put("similarity_score", sr.getHighestMatchScore());
            if (sr.getEntityType() != null) {
                features.put("screened_entity_type", sr.getEntityType().name());
            }
            if (sr.hasMatches()) {
                com.posgateway.aml.model.ScreeningResult.Match match = sr.getMatches().get(0);
                features.put("match_type", match.getMatchType() != null ? match.getMatchType().name() : null);
                features.put("list_type", match.getListName());
                features.put("pep_level", match.getPepLevel());
                features.put("alias_flag", match.getMatchType() == com.posgateway.aml.model.ScreeningResult.MatchType.ALIAS_MATCH);
                features.put("dob_relation", deriveDobRelation(match));
                features.put("nationality_relation", match.getNationality() != null ? "provided" : "unknown");
            }
        }
        return features;
    }

    private static String deriveDobRelation(com.posgateway.aml.model.ScreeningResult.Match match) {
        if (match.getDateOfBirth() != null) {
            return "exact";
        }
        return "unknown";
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
            if (legalNameResult.getStatus() != ScreeningResult.ScreeningStatus.UNAVAILABLE) {
                screeningCacheService.cacheScreeningResult(
                        String.valueOf(merchant.getMerchantId()), "MERCHANT", legalNameResult);
            }
        }

        if (legalNameResult.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE) {
            throw new ScreeningUnavailableException();
        }

        if (legalNameResult.hasMatches()) {
            return new ScreeningMatch(
                    merchant.getMerchantId().toString(),
                    "MERCHANT",
                    merchant.getLegalName(),
                    legalNameResult,
                    isConfirmedHit(legalNameResult));
        }

        // Screen trading name if different
        if (merchant.getTradingName() != null &&
                !merchant.getTradingName().equals(merchant.getLegalName())) {
            ScreeningResult tradingNameResult = aerospikeScreeningService.screenName(
                    merchant.getTradingName(),
                    ScreeningResult.EntityType.ORGANIZATION);

            if (tradingNameResult.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE) {
                throw new ScreeningUnavailableException();
            }

            if (tradingNameResult.hasMatches()) {
                return new ScreeningMatch(
                        merchant.getMerchantId().toString(),
                        "MERCHANT",
                        merchant.getTradingName(),
                        tradingNameResult,
                        isConfirmedHit(tradingNameResult));
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

        if (result.getStatus() == ScreeningResult.ScreeningStatus.UNAVAILABLE) {
            throw new ScreeningUnavailableException();
        }

        if (result.hasMatches()) {
            return new ScreeningMatch(
                    name,
                    "COUNTERPARTY",
                    name,
                    result,
                    isConfirmedHit(result));
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
        private final boolean screeningUnavailable;

        public TransactionScreeningResult(Long transactionId, List<ScreeningMatch> matches, boolean shouldBlock,
                boolean screeningUnavailable) {
            this.transactionId = transactionId;
            this.matches = matches != null ? matches : new ArrayList<>();
            this.shouldBlock = shouldBlock;
            this.screeningUnavailable = screeningUnavailable;
        }

        public static TransactionScreeningResult clear(Long transactionId) {
            return new TransactionScreeningResult(transactionId, new ArrayList<>(), false, false);
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

        public boolean isScreeningUnavailable() {
            return screeningUnavailable;
        }

        /**
         * True when there are matches but none of them is a confirmed hit (or blocking was
         * disabled), i.e. the transaction must be held for sanctions disambiguation / review.
         */
        public boolean requiresReview() {
            return hasMatches() && !shouldBlock;
        }
    }

    private static final class ScreeningUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;
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
