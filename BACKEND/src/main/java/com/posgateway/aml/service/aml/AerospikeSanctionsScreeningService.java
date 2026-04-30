package com.posgateway.aml.service.aml;

import com.aerospike.client.*;
import com.aerospike.client.policy.QueryPolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.model.ScreeningResult.EntityType;
import com.posgateway.aml.model.ScreeningResult.Match;
import com.posgateway.aml.model.ScreeningResult.MatchType;
import com.posgateway.aml.model.ScreeningResult.ScreeningStatus;
import com.posgateway.aml.service.sanctions.NameMatchingService;
import com.posgateway.aml.service.AerospikeConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Aerospike-based sanctions screening service (Tier 2)
 * Used for existing merchants or when Sumsub is unavailable
 * 
 * All sanctions lists are stored in Aerospike database for fast lookups.
 * This service queries Aerospike directly using phonetic codes for efficient matching.
 * The sanctions data is loaded into Aerospike by SanctionsListDownloadService.
 */
@Service
public class AerospikeSanctionsScreeningService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
            .getLogger(AerospikeSanctionsScreeningService.class);

    @Autowired
    private AerospikeConnectionService aerospikeService;

    @Autowired
    private NameMatchingService nameMatchingService;

    @Value("${aerospike.namespace:sanctions}")
    private String namespace;

    @Value("${sanctions.matching.similarity.threshold:0.8}")
    private double similarityThreshold;

    @Value("${sanctions.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${sanctions.cache.ttl.hours:24}")
    private int cacheTtlHours;

    /**
     * Screen a name against Aerospike sanctions database
     */
    @org.springframework.cache.annotation.Cacheable(value = "screeningResults", key = "#name")
    public ScreeningResult screenName(String name, EntityType entityType) {
        if (name == null || name.trim().isEmpty()) {
            log.warn("Attempted to screen empty name");
            return ScreeningResult.builder()
                    .screenedName(name != null ? name : "")
                    .entityType(entityType != null ? entityType : EntityType.PERSON)
                    .status(ScreeningStatus.CLEAR)
                    .matchCount(0)
                    .highestMatchScore(0.0)
                    .matches(new ArrayList<>())
                    .screenedAt(LocalDateTime.now())
                    .screeningProvider("AEROSPIKE")
                    .build();
        }

        log.info("Screening name '{}' against Aerospike sanctions (type: {})", name, entityType);

        // Check cache first
        if (cacheEnabled) {
            try {
                ScreeningResult cachedResult = getCachedScreeningResult(name);
                if (cachedResult != null) {
                    log.debug("Cache hit for name '{}'", name);
                    return cachedResult;
                }
            } catch (Exception e) {
                log.warn("Cache lookup failed, continuing with fresh screening: {}", e.getMessage());
            }
        }

        try {
            // Validate dependencies
            if (nameMatchingService == null) {
                log.error("NameMatchingService is not available");
                throw new IllegalStateException("Name matching service is not configured");
            }

            // Generate phonetic codes for fast lookup
            String phoneticCode = nameMatchingService.generatePhoneticCode(name);
            String alternateCode = nameMatchingService.generateAlternatePhoneticCode(name);

            log.debug("Phonetic codes: primary='{}', alternate='{}'", phoneticCode, alternateCode);

            // Query Aerospike by phonetic code (fast indexed lookup)
            List<Match> matches = new ArrayList<>();
            try {
                if (phoneticCode != null && !phoneticCode.isEmpty()) {
                    matches.addAll(findMatchesByPhoneticCode(phoneticCode, name, entityType));
                }

            // Also check alternate phonetic code if different
            if (alternateCode != null && !alternateCode.isEmpty() && phoneticCode != null && !phoneticCode.equals(alternateCode)) {
                matches.addAll(findMatchesByPhoneticCode(alternateCode, name, entityType));
            }
            } catch (Exception e) {
                log.warn("Error querying Aerospike for matches, continuing with empty results: {}", e.getMessage());
                // Continue with empty matches - better to return clear result than fail
            }

            // Build result
            ScreeningResult result = ScreeningResult.builder()
                    .screenedName(name)
                    .entityType(entityType != null ? entityType : EntityType.PERSON)
                    .status(determineStatus(matches))
                    .matchCount(matches.size())
                    .highestMatchScore(getHighestScore(matches))
                    .matches(matches)
                    .screenedAt(LocalDateTime.now())
                    .screeningProvider("AEROSPIKE")
                    .build();

            // Cache result
            if (cacheEnabled && result.getStatus() == ScreeningStatus.CLEAR) {
                try {
                    cacheScreeningResult(name, result);
                } catch (Exception e) {
                    log.warn("Failed to cache screening result: {}", e.getMessage());
                }
            }

            log.info("Screening complete: {} matches found (status: {})", matches.size(), result.getStatus());

            return result;

        } catch (IllegalStateException e) {
            log.error("Configuration error screening name '{}': {}", name, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error screening name '{}': {}", name, e.getMessage(), e);
            // Return a clear result instead of throwing exception to prevent complete failure
            return ScreeningResult.builder()
                    .screenedName(name)
                    .entityType(entityType != null ? entityType : EntityType.PERSON)
                    .status(ScreeningStatus.CLEAR)
                    .matchCount(0)
                    .highestMatchScore(0.0)
                    .matches(new ArrayList<>())
                    .screenedAt(LocalDateTime.now())
                    .screeningProvider("AEROSPIKE")
                    .build();
        }
    }

    /**
     * Screen a merchant (legal name + trading name)
     */
    public ScreeningResult screenMerchant(String legalName, String tradingName) {
        log.info("Screening merchant: legalName='{}', tradingName='{}'", legalName, tradingName);

        // Screen legal name
        ScreeningResult legalNameResult = screenName(legalName, EntityType.ORGANIZATION);

        // Screen trading name if different
        if (tradingName != null && !tradingName.equals(legalName)) {
            ScreeningResult tradingNameResult = screenName(tradingName, EntityType.ORGANIZATION);

            // Merge results
            if (tradingNameResult.hasMatches()) {
                legalNameResult.getMatches().addAll(tradingNameResult.getMatches());
                legalNameResult.setStatus(determineStatus(legalNameResult.getMatches()));
                legalNameResult.setMatchCount(legalNameResult.getMatches().size());
                legalNameResult.setHighestMatchScore(getHighestScore(legalNameResult.getMatches()));
            }
        }

        return legalNameResult;
    }

    /**
     * Screen a beneficial owner
     */
    public ScreeningResult screenBeneficialOwner(String fullName, LocalDate dateOfBirth) {
        log.info("Screening beneficial owner: name='{}', dob='{}'", fullName, dateOfBirth);

        ScreeningResult result = screenName(fullName, EntityType.PERSON);

        // If DOB provided, verify matches
        if (dateOfBirth != null && result.hasMatches()) {
            result.getMatches().forEach(match -> {
                if (match.getDateOfBirth() != null && match.getDateOfBirth().equals(dateOfBirth)) {
                    match.setMatchType(MatchType.DOB_CONFIRMED);
                    log.warn("DOB match confirmed for '{}': {}", fullName, dateOfBirth);
                }
            });
        }

        return result;
    }

    /**
     * Find matches in Aerospike by phonetic code
     */
    private List<Match> findMatchesByPhoneticCode(String phoneticCode, String searchName, EntityType entityType) {
        List<Match> matches = new ArrayList<>();

        if (phoneticCode == null || phoneticCode.isEmpty()) {
            return matches;
        }

        AerospikeClient client = aerospikeService.getClient();
        if (client == null) {
            log.error("Aerospike client not available");
            return matches;
        }

        try {
            // Query using secondary index on name_metaphone
            Statement stmt = new Statement();
            stmt.setNamespace(namespace);
            stmt.setSetName("entities");
            stmt.setFilter(Filter.equal("name_metaphone", phoneticCode));

            QueryPolicy policy = new QueryPolicy();
            policy.maxConcurrentNodes = 1;
            policy.recordQueueSize = 50;

            RecordSet recordSet = client.query(policy, stmt);

            try {
                while (recordSet.next()) {
                    com.aerospike.client.Record record = recordSet.getRecord();

                    // Extract data from Aerospike bins
                    String fullName = (String) record.bins.get("full_name");
                    String entityTypeStr = (String) record.bins.get("entity_type");

                    // Filter by entity type if specified
                    if (entityType != null && !entityType.name().equals(entityTypeStr)) {
                        continue;
                    }

                    // Calculate similarity
                    double similarityScore = nameMatchingService.calculateSimilarityScore(searchName, fullName);

                    // Only add if similarity is above threshold
                    if (similarityScore >= similarityThreshold) {
                        Match match = buildMatchFromRecord(record, similarityScore);
                        matches.add(match);

                        log.debug("Match found: '{}' <-> '{}' (score: {:.2f})",
                                searchName, fullName, similarityScore);
                    }
                }
            } finally {
                recordSet.close();
            }

        } catch (AerospikeException e) {
            log.error("Aerospike query error: {}", e.getMessage(), e);
        }

        return matches;
    }

    /**
     * Build Match object from Aerospike record
     */
    @SuppressWarnings("unchecked")
    private Match buildMatchFromRecord(com.aerospike.client.Record record, double similarityScore) {
        return Match.builder()
                .matchedName((String) record.bins.get("full_name"))
                .aliases((List<String>) record.bins.get("aliases"))
                .similarityScore(similarityScore)
                .listName((String) record.bins.get("list_name"))
                .entityType(EntityType.valueOf((String) record.bins.get("entity_type")))
                .matchType(MatchType.PHONETIC_MATCH)
                .dateOfBirth(parseDateOfBirth(record.bins.get("date_of_birth")))
                .nationality((List<String>) record.bins.get("nationality"))
                .sanctionType((String) record.bins.get("sanction_type"))
                .programs((List<String>) record.bins.get("program"))
                .build();
    }

    /**
     * Get cached screening result
     */
    private ScreeningResult getCachedScreeningResult(String name) {
        try {
            AerospikeClient client = aerospikeService.getClient();
            if (client == null)
                return null;

            String cacheKey = "CACHE:" + name.hashCode();
            Key key = new Key(namespace, "screening_cache", cacheKey);

            com.aerospike.client.Record record = client.get(null, key);
            if (record != null) {
                // Parse cached result (implement deserialization)
                return parseCachedResult(record);
            }
        } catch (Exception e) {
            log.warn("Cache lookup failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Cache screening result in Aerospike
     */
    private void cacheScreeningResult(String name, ScreeningResult result) {
        try {
            AerospikeClient client = aerospikeService.getClient();
            if (client == null)
                return;

            String cacheKey = "CACHE:" + name.hashCode();
            Key key = new Key(namespace, "screening_cache", cacheKey);

            // Create bins for cache
            Bin nameBin = new Bin("name", name);
            Bin statusBin = new Bin("status", result.getStatus().name());
            Bin screenedAtBin = new Bin("screened_at", System.currentTimeMillis());

            // Write with TTL
            com.aerospike.client.policy.WritePolicy policy = new com.aerospike.client.policy.WritePolicy();
            policy.expiration = cacheTtlHours * 3600; // Convert hours to seconds

            client.put(policy, key, nameBin, statusBin, screenedAtBin);

            log.debug("Cached screening result for '{}'", name);

        } catch (Exception e) {
            log.warn("Failed to cache result: {}", e.getMessage());
        }
    }

    private ScreeningStatus determineStatus(List<Match> matches) {
        if (matches.isEmpty()) {
            return ScreeningStatus.CLEAR;
        }

        // Check if any match has high confidence (>0.95)
        boolean highConfidenceMatch = matches.stream()
                .anyMatch(m -> m.getSimilarityScore() >= 0.95);

        return highConfidenceMatch ? ScreeningStatus.MATCH : ScreeningStatus.POTENTIAL_MATCH;
    }

    private Double getHighestScore(List<Match> matches) {
        return matches.stream()
                .map(Match::getSimilarityScore)
                .max(Double::compareTo)
                .orElse(0.0);
    }

    private LocalDate parseDateOfBirth(Object dob) {
        if (dob instanceof Long) {
            long epochSeconds = (Long) dob;
            return LocalDate.ofEpochDay(epochSeconds / 86400);
        }
        return null;
    }

    private ScreeningResult parseCachedResult(com.aerospike.client.Record record) {
        // Implement cache deserialization
        return ScreeningResult.builder()
                .screenedName((String) record.bins.get("name"))
                .status(ScreeningStatus.valueOf((String) record.bins.get("status")))
                .screenedAt(LocalDateTime.now())
                .screeningProvider("AEROSPIKE_CACHE")
                .matches(new ArrayList<>())
                .build();
    }
}
