package com.posgateway.aml.dto.psp;

public class PspLoginRequest {
    private String email;
    private String password;

    public PspLoginRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
