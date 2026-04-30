package com.posgateway.aml.dto.auth;

public class PasswordResetRequest {
    private String identifier; // username or email

    public PasswordResetRequest() {
    }

    public PasswordResetRequest(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}


