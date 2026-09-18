package com.posgateway.aml.dto.auth;

/**
 * Password reset confirmation request.
 * Only requires the token - password will be reset to default password.
 */
public class PasswordResetConfirmRequest {
    private String token;

    public PasswordResetConfirmRequest() {
    }

    public PasswordResetConfirmRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}


