package com.hokeka.aml.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for {@code POST /internal/v1/sanctions/screen}.
 *
 * <p>Sanctions data is global, not PSP-scoped — {@code pspId} is informational only,
 * carried for audit logging on the microservice side.
 */
public class SanctionsScreenRequest {

    @JsonProperty("name")
    private String name;

    /** {@code "PERSON"}, {@code "ORGANIZATION"}, or {@code null} for any. */
    @JsonProperty("type")
    private String type;

    @JsonProperty("pspId")
    private Long pspId;

    public SanctionsScreenRequest() {}

    public SanctionsScreenRequest(String name, String type, Long pspId) {
        this.name = name;
        this.type = type;
        this.pspId = pspId;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
}
