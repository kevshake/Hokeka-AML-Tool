package com.posgateway.aml.dto.psp;

import java.util.List;

public class PspUserCreationRequest {
    private Long pspId;
    private String email;
    private String fullName;
    private String password; // Raw password
    private String role;
    private List<String> permissions;

    public PspUserCreationRequest() {
    }

    public PspUserCreationRequest(Long pspId, String email, String fullName, String password, String role,
            List<String> permissions) {
        this.pspId = pspId;
        this.email = email;
        this.fullName = fullName;
        this.password = password;
        this.role = role;
        this.permissions = permissions;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public static PspUserCreationRequestBuilder builder() {
        return new PspUserCreationRequestBuilder();
    }

    public static class PspUserCreationRequestBuilder {
        private Long pspId;
        private String email;
        private String fullName;
        private String password;
        private String role;
        private List<String> permissions;

        PspUserCreationRequestBuilder() {
        }

        public PspUserCreationRequestBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public PspUserCreationRequestBuilder email(String email) {
            this.email = email;
            return this;
        }

        public PspUserCreationRequestBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public PspUserCreationRequestBuilder password(String password) {
            this.password = password;
            return this;
        }

        public PspUserCreationRequestBuilder role(String role) {
            this.role = role;
            return this;
        }

        public PspUserCreationRequestBuilder permissions(List<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public PspUserCreationRequest build() {
            return new PspUserCreationRequest(pspId, email, fullName, password, role, permissions);
        }

        public String toString() {
            return "PspUserCreationRequest.PspUserCreationRequestBuilder(pspId=" + this.pspId + ", email=" + this.email
                    + ", fullName=" + this.fullName + ", password=" + this.password + ", role=" + this.role
                    + ", permissions=" + this.permissions + ")";
        }
    }
}
