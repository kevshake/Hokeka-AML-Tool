package com.posgateway.aml.dto.auth;

public class EmergencyPasswordResetRequest {
    private String identifier; // username or email

    public EmergencyPasswordResetRequest() {
    }

    public EmergencyPasswordResetRequest(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}


