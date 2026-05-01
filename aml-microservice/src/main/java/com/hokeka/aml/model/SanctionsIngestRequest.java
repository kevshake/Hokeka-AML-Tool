package com.hokeka.aml.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk-ingest payload for {@code POST /internal/v1/sanctions/ingest}.
 * BACKEND's daily OpenSanctions downloader pushes parsed entities to this endpoint.
 */
public class SanctionsIngestRequest {

    @JsonProperty("entities")
    private List<SanctionsEntity> entities = new ArrayList<>();

    public SanctionsIngestRequest() {}

    public SanctionsIngestRequest(List<SanctionsEntity> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    public List<SanctionsEntity> getEntities() { return entities; }
    public void setEntities(List<SanctionsEntity> entities) { this.entities = entities; }

    public static class SanctionsEntity {
        @JsonProperty("entityId")
        private String entityId;
        @JsonProperty("name")
        private String name;
        @JsonProperty("aliases")
        private List<String> aliases = new ArrayList<>();
        @JsonProperty("type")
        private String type; // PERSON | ORGANIZATION | UNKNOWN
        @JsonProperty("listName")
        private String listName;
        @JsonProperty("country")
        private String country;
        @JsonProperty("birthDate")
        private String birthDate;

        public SanctionsEntity() {}

        public SanctionsEntity(String entityId, String name, List<String> aliases, String type,
                               String listName, String country, String birthDate) {
            this.entityId = entityId;
            this.name = name;
            this.aliases = aliases != null ? aliases : new ArrayList<>();
            this.type = type;
            this.listName = listName;
            this.country = country;
            this.birthDate = birthDate;
        }

        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getAliases() { return aliases; }
        public void setAliases(List<String> aliases) { this.aliases = aliases; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getListName() { return listName; }
        public void setListName(String listName) { this.listName = listName; }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }

        public String getBirthDate() { return birthDate; }
        public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    }
}
