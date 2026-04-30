package com.posgateway.aml.service.sanctions;

import org.apache.commons.codec.language.DoubleMetaphone;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Name Matching Service using hybrid Double Metaphone + Levenshtein approach
 * 
 * Strategy:
 * 1. Generate phonetic codes (Double Metaphone) for fast pre-filtering
 * 2. Calculate exact similarity (Levenshtein Distance) for precise scoring
 * 3. Combined approach provides both speed and accuracy
 */
@Service
public class NameMatchingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NameMatchingService.class);

    private final DoubleMetaphone doubleMetaphone;
    private final LevenshteinDistance levenshteinDistance;

    @Value("${sanctions.matching.levenshtein.threshold:3}")
    private int levenshteinThreshold;

    @Value("${sanctions.matching.similarity.threshold:0.8}")
    private double similarityThreshold;

    public NameMatchingService() {
        this.doubleMetaphone = new DoubleMetaphone();
        this.doubleMetaphone.setMaxCodeLen(10); // Longer codes for better accuracy
        this.levenshteinDistance = new LevenshteinDistance();
    }

    /**
     * Generate primary phonetic code for a name
     */
    public String generatePhoneticCode(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        // Clean and normalize name
        String cleaned = cleanName(name);
        return doubleMetaphone.doubleMetaphone(cleaned);
    }

    /**
     * Generate alternative phonetic code for a name
     */
    public String generateAlternatePhoneticCode(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        String cleaned = cleanName(name);
        return doubleMetaphone.doubleMetaphone(cleaned, true); // Get alternate encoding
    }

    /**
     * Calculate Levenshtein distance between two names
     * Lower distance = more similar
     */
    public int calculateLevenshteinDistance(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return Integer.MAX_VALUE;
        }

        String cleaned1 = cleanName(name1);
        String cleaned2 = cleanName(name2);

        return levenshteinDistance.apply(cleaned1, cleaned2);
    }

    /**
     * Calculate similarity score (0.0 to 1.0)
     * 1.0 = exact match, 0.0 = completely different
     */
    public double calculateSimilarityScore(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return 0.0;
        }

        String cleaned1 = cleanName(name1);
        String cleaned2 = cleanName(name2);

        if (cleaned1.equals(cleaned2)) {
            return 1.0;
        }

        int distance = levenshteinDistance.apply(cleaned1, cleaned2);
        int maxLength = Math.max(cleaned1.length(), cleaned2.length());

        if (maxLength == 0) {
            return 0.0;
        }

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Check if two names match based on phonetic similarity and Levenshtein
     * distance
     * 
     * @return true if names are considered a match
     */
    public boolean isMatch(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }

        // Step 1: Check phonetic match (fast pre-filter)
        String phonetic1 = generatePhoneticCode(name1);
        String phonetic2 = generatePhoneticCode(name2);

        boolean phoneticMatch = phonetic1.equals(phonetic2);

        // Also check alternate phonetic codes
        if (!phoneticMatch) {
            String alt1 = generateAlternatePhoneticCode(name1);
            String alt2 = generateAlternatePhoneticCode(name2);
            phoneticMatch = alt1.equals(alt2) || phonetic1.equals(alt2) || alt1.equals(phonetic2);
        }

        // If no phonetic match, likely not a match
        if (!phoneticMatch) {
            return false;
        }

        // Step 2: Calculate Levenshtein distance for precise matching
        int distance = calculateLevenshteinDistance(name1, name2);

        // Step 3: Calculate similarity score
        double similarityScore = calculateSimilarityScore(name1, name2);

        // Match if distance is within threshold OR similarity is high enough
        boolean isMatch = distance <= levenshteinThreshold || similarityScore >= similarityThreshold;

        if (isMatch) {
            log.debug("Name match found: '{}' <-> '{}' (distance={}, similarity={:.2f})",
                    name1, name2, distance, similarityScore);
        }

        return isMatch;
    }

    /**
     * Get match result with detailed scoring
     */
    public MatchResult getMatchResult(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return new MatchResult(name1, name2, "", "", false, Integer.MAX_VALUE, 0.0, false);
        }

        String phonetic1 = generatePhoneticCode(name1);
        String phonetic2 = generatePhoneticCode(name2);
        boolean phoneticMatch = phonetic1.equals(phonetic2);

        // Also check alternate phonetic codes
        if (!phoneticMatch) {
            String alt1 = generateAlternatePhoneticCode(name1);
            String alt2 = generateAlternatePhoneticCode(name2);
            phoneticMatch = alt1.equals(alt2) || phonetic1.equals(alt2) || alt1.equals(phonetic2);
        }

        int distance = calculateLevenshteinDistance(name1, name2);
        double similarityScore = calculateSimilarityScore(name1, name2);
        
        // Calculate match directly without recursion
        boolean isMatch = phoneticMatch && (distance <= levenshteinThreshold || similarityScore >= similarityThreshold);

        return new MatchResult(name1, name2, phonetic1, phonetic2, phoneticMatch, distance, similarityScore, isMatch);
    }

    /**
     * Clean and normalize name for matching
     * - Remove special characters
     * - Convert to uppercase
     * - Remove extra whitespace
     */
    private String cleanName(String name) {
        if (name == null) {
            return "";
        }

        return name.toUpperCase()
                .replaceAll("[^A-Z0-9\\s]", "") // Remove special chars
                .replaceAll("\\s+", " ") // Normalize whitespace
                .trim();
    }

    /**
     * Result object containing match details
     */
    public static class MatchResult {
        private String name1;
        private String name2;
        private String phoneticCode1;
        private String phoneticCode2;
        private boolean phoneticMatch;
        private int levenshteinDistance;
        private double similarityScore;
        private boolean isMatch;

        public MatchResult(String name1, String name2, String phoneticCode1, String phoneticCode2,
                boolean phoneticMatch, int levenshteinDistance, double similarityScore, boolean isMatch) {
            this.name1 = name1;
            this.name2 = name2;
            this.phoneticCode1 = phoneticCode1;
            this.phoneticCode2 = phoneticCode2;
            this.phoneticMatch = phoneticMatch;
            this.levenshteinDistance = levenshteinDistance;
            this.similarityScore = similarityScore;
            this.isMatch = isMatch;
        }

        public String getName1() {
            return name1;
        }

        public String getName2() {
            return name2;
        }

        public String getPhoneticCode1() {
            return phoneticCode1;
        }

        public String getPhoneticCode2() {
            return phoneticCode2;
        }

        public boolean isPhoneticMatch() {
            return phoneticMatch;
        }

        public int getLevenshteinDistance() {
            return levenshteinDistance;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public boolean isMatch() {
            return isMatch;
        }
    }
}
