package com.posgateway.aml.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Screening result model for sanctions and PEP screening
 */
public class ScreeningResult {

    private String screenedName;
    private EntityType entityType;
    private ScreeningStatus status;
    private Integer matchCount;
    private Double highestMatchScore;
    private List<Match> matches = new ArrayList<>();
    private LocalDateTime screenedAt;
    private String screeningProvider; // AEROSPIKE, SUMSUB, COMPLYADVANTAGE

    public ScreeningResult() {
    }

    public ScreeningResult(String screenedName, EntityType entityType, ScreeningStatus status, Integer matchCount,
            Double highestMatchScore, List<Match> matches, LocalDateTime screenedAt, String screeningProvider) {
        this.screenedName = screenedName;
        this.entityType = entityType;
        this.status = status;
        this.matchCount = matchCount;
        this.highestMatchScore = highestMatchScore;
        this.matches = matches != null ? matches : new ArrayList<>();
        this.screenedAt = screenedAt;
        this.screeningProvider = screeningProvider;
    }

    public String getScreenedName() {
        return screenedName;
    }

    public void setScreenedName(String screenedName) {
        this.screenedName = screenedName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public ScreeningStatus getStatus() {
        return status;
    }

    public void setStatus(ScreeningStatus status) {
        this.status = status;
    }

    public Integer getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(Integer matchCount) {
        this.matchCount = matchCount;
    }

    public Double getHighestMatchScore() {
        return highestMatchScore;
    }

    public void setHighestMatchScore(Double highestMatchScore) {
        this.highestMatchScore = highestMatchScore;
    }

    public List<Match> getMatches() {
        return matches;
    }

    public void setMatches(List<Match> matches) {
        this.matches = matches;
    }

    public LocalDateTime getScreenedAt() {
        return screenedAt;
    }

    public void setScreenedAt(LocalDateTime screenedAt) {
        this.screenedAt = screenedAt;
    }

    public String getScreeningProvider() {
        return screeningProvider;
    }

    public void setScreeningProvider(String screeningProvider) {
        this.screeningProvider = screeningProvider;
    }

    /**
     * Check if screening found any matches
     */
    public boolean hasMatches() {
        return matches != null && !matches.isEmpty();
    }

    /**
     * Get match count
     */

    public static ScreeningResultBuilder builder() {
        return new ScreeningResultBuilder();
    }

    public static class ScreeningResultBuilder {
        private String screenedName;
        private EntityType entityType;
        private ScreeningStatus status;
        private Integer matchCount;
        private Double highestMatchScore;
        private List<Match> matches = new ArrayList<>();
        private LocalDateTime screenedAt;
        private String screeningProvider;

        ScreeningResultBuilder() {
        }

        public ScreeningResultBuilder screenedName(String screenedName) {
            this.screenedName = screenedName;
            return this;
        }

        public ScreeningResultBuilder entityType(EntityType entityType) {
            this.entityType = entityType;
            return this;
        }

        public ScreeningResultBuilder status(ScreeningStatus status) {
            this.status = status;
            return this;
        }

        public ScreeningResultBuilder matchCount(Integer matchCount) {
            this.matchCount = matchCount;
            return this;
        }

        public ScreeningResultBuilder highestMatchScore(Double highestMatchScore) {
            this.highestMatchScore = highestMatchScore;
            return this;
        }

        public ScreeningResultBuilder matches(List<Match> matches) {
            this.matches = matches;
            return this;
        }

        public ScreeningResultBuilder screenedAt(LocalDateTime screenedAt) {
            this.screenedAt = screenedAt;
            return this;
        }

        public ScreeningResultBuilder screeningProvider(String screeningProvider) {
            this.screeningProvider = screeningProvider;
            return this;
        }

        public ScreeningResult build() {
            return new ScreeningResult(screenedName, entityType, status, matchCount, highestMatchScore, matches,
                    screenedAt, screeningProvider);
        }

        public String toString() {
            return "ScreeningResult.ScreeningResultBuilder(screenedName=" + this.screenedName + ", entityType="
                    + this.entityType + ", status=" + this.status + ", matchCount=" + this.matchCount
                    + ", highestMatchScore=" + this.highestMatchScore + ", matches=" + this.matches + ", screenedAt="
                    + this.screenedAt + ", screeningProvider=" + this.screeningProvider + ")";
        }
    }

    /**
     * Individual match within screening result
     */
    public static class Match {
        private String matchedName;
        private List<String> aliases;
        private Double similarityScore;
        private String listName; // OFAC_SDN, UN_SC, EU_FSF, PEP
        private EntityType entityType;
        private MatchType matchType; // NAME_MATCH, ALIAS_MATCH, DOB_CONFIRMED

        // Additional details
        private LocalDate dateOfBirth;
        private List<String> nationality;
        private String sanctionType;
        private List<String> programs;
        private String pepLevel; // CURRENT, FORMER, RCA
        private String position;

        // Raw data for audit
        private Map<String, Object> rawData;

        public Match() {
        }

        public Match(String matchedName, List<String> aliases, Double similarityScore, String listName,
                EntityType entityType, MatchType matchType, LocalDate dateOfBirth, List<String> nationality,
                String sanctionType, List<String> programs, String pepLevel, String position,
                Map<String, Object> rawData) {
            this.matchedName = matchedName;
            this.aliases = aliases;
            this.similarityScore = similarityScore;
            this.listName = listName;
            this.entityType = entityType;
            this.matchType = matchType;
            this.dateOfBirth = dateOfBirth;
            this.nationality = nationality;
            this.sanctionType = sanctionType;
            this.programs = programs;
            this.pepLevel = pepLevel;
            this.position = position;
            this.rawData = rawData;
        }

        public String getMatchedName() {
            return matchedName;
        }

        public void setMatchedName(String matchedName) {
            this.matchedName = matchedName;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases;
        }

        public Double getSimilarityScore() {
            return similarityScore;
        }

        public void setSimilarityScore(Double similarityScore) {
            this.similarityScore = similarityScore;
        }

        public String getListName() {
            return listName;
        }

        public void setListName(String listName) {
            this.listName = listName;
        }

        public EntityType getEntityType() {
            return entityType;
        }

        public void setEntityType(EntityType entityType) {
            this.entityType = entityType;
        }

        public MatchType getMatchType() {
            return matchType;
        }

        public void setMatchType(MatchType matchType) {
            this.matchType = matchType;
        }

        public LocalDate getDateOfBirth() {
            return dateOfBirth;
        }

        public void setDateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public List<String> getNationality() {
            return nationality;
        }

        public void setNationality(List<String> nationality) {
            this.nationality = nationality;
        }

        public String getSanctionType() {
            return sanctionType;
        }

        public void setSanctionType(String sanctionType) {
            this.sanctionType = sanctionType;
        }

        public List<String> getPrograms() {
            return programs;
        }

        public void setPrograms(List<String> programs) {
            this.programs = programs;
        }

        public String getPepLevel() {
            return pepLevel;
        }

        public void setPepLevel(String pepLevel) {
            this.pepLevel = pepLevel;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public Map<String, Object> getRawData() {
            return rawData;
        }

        public void setRawData(Map<String, Object> rawData) {
            this.rawData = rawData;
        }

        public static MatchBuilder builder() {
            return new MatchBuilder();
        }

        public static class MatchBuilder {
            private String matchedName;
            private List<String> aliases;
            private Double similarityScore;
            private String listName;
            private EntityType entityType;
            private MatchType matchType;
            private LocalDate dateOfBirth;
            private List<String> nationality;
            private String sanctionType;
            private List<String> programs;
            private String pepLevel;
            private String position;
            private Map<String, Object> rawData;

            MatchBuilder() {
            }

            public MatchBuilder matchedName(String matchedName) {
                this.matchedName = matchedName;
                return this;
            }

            public MatchBuilder aliases(List<String> aliases) {
                this.aliases = aliases;
                return this;
            }

            public MatchBuilder similarityScore(Double similarityScore) {
                this.similarityScore = similarityScore;
                return this;
            }

            public MatchBuilder listName(String listName) {
                this.listName = listName;
                return this;
            }

            public MatchBuilder entityType(EntityType entityType) {
                this.entityType = entityType;
                return this;
            }

            public MatchBuilder matchType(MatchType matchType) {
                this.matchType = matchType;
                return this;
            }

            public MatchBuilder dateOfBirth(LocalDate dateOfBirth) {
                this.dateOfBirth = dateOfBirth;
                return this;
            }

            public MatchBuilder nationality(List<String> nationality) {
                this.nationality = nationality;
                return this;
            }

            public MatchBuilder sanctionType(String sanctionType) {
                this.sanctionType = sanctionType;
                return this;
            }

            public MatchBuilder programs(List<String> programs) {
                this.programs = programs;
                return this;
            }

            public MatchBuilder pepLevel(String pepLevel) {
                this.pepLevel = pepLevel;
                return this;
            }

            public MatchBuilder position(String position) {
                this.position = position;
                return this;
            }

            public MatchBuilder rawData(Map<String, Object> rawData) {
                this.rawData = rawData;
                return this;
            }

            public Match build() {
                return new Match(matchedName, aliases, similarityScore, listName, entityType, matchType, dateOfBirth,
                        nationality, sanctionType, programs, pepLevel, position, rawData);
            }

            public String toString() {
                return "ScreeningResult.Match.MatchBuilder(matchedName=" + this.matchedName + ", aliases="
                        + this.aliases + ", similarityScore=" + this.similarityScore + ", listName=" + this.listName
                        + ", entityType=" + this.entityType + ", matchType=" + this.matchType + ", dateOfBirth="
                        + this.dateOfBirth + ", nationality=" + this.nationality + ", sanctionType=" + this.sanctionType
                        + ", programs=" + this.programs + ", pepLevel=" + this.pepLevel + ", position=" + this.position
                        + ", rawData=" + this.rawData + ")";
            }
        }
    }

    public enum ScreeningStatus {
        CLEAR,
        POTENTIAL_MATCH,
        MATCH
    }

    public enum EntityType {
        PERSON,
        ORGANIZATION,
        VESSEL,
        UNKNOWN
    }

    public enum MatchType {
        NAME_MATCH,
        ALIAS_MATCH,
        DOB_CONFIRMED,
        PHONETIC_MATCH
    }
}
