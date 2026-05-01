package com.hokeka.aml.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Response payload from {@code POST /internal/v1/sanctions/screen}.
 *
 * <p>{@code status} is one of:
 * <ul>
 *   <li>{@code CLEAR}    — no match >= the configured similarity threshold</li>
 *   <li>{@code REVIEW}   — at least one match in [threshold, 0.95)</li>
 *   <li>{@code FLAGGED}  — at least one match >= 0.95</li>
 * </ul>
 */
public class SanctionsScreenResponse {

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private String status;

    @JsonProperty("matches")
    private List<MatchDto> matches = new ArrayList<>();

    @JsonProperty("checkedAt")
    private Instant checkedAt;

    public SanctionsScreenResponse() {}

    public SanctionsScreenResponse(String name, String status, List<MatchDto> matches, Instant checkedAt) {
        this.name = name;
        this.status = status;
        this.matches = matches != null ? matches : new ArrayList<>();
        this.checkedAt = checkedAt;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<MatchDto> getMatches() { return matches; }
    public void setMatches(List<MatchDto> matches) { this.matches = matches; }

    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }

    public static class MatchDto {
        @JsonProperty("matchedName")
        private String matchedName;
        @JsonProperty("similarityScore")
        private double similarityScore;
        @JsonProperty("listName")
        private String listName;
        @JsonProperty("entityId")
        private String entityId;

        public MatchDto() {}

        public MatchDto(String matchedName, double similarityScore, String listName, String entityId) {
            this.matchedName = matchedName;
            this.similarityScore = similarityScore;
            this.listName = listName;
            this.entityId = entityId;
        }

        public String getMatchedName() { return matchedName; }
        public void setMatchedName(String matchedName) { this.matchedName = matchedName; }

        public double getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }

        public String getListName() { return listName; }
        public void setListName(String listName) { this.listName = listName; }

        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
    }
}
