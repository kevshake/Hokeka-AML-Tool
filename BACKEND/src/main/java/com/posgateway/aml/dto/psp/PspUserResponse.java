package com.posgateway.aml.dto.psp;

public class PspUserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String status;

    public PspUserResponse() {
    }

    public PspUserResponse(Long id, String email, String fullName, String role, String status) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static PspUserResponseBuilder builder() {
        return new PspUserResponseBuilder();
    }

    public static class PspUserResponseBuilder {
        private Long id;
        private String email;
        private String fullName;
        private String role;
        private String status;

        PspUserResponseBuilder() {
        }

        public PspUserResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public PspUserResponseBuilder email(String email) {
            this.email = email;
            return this;
        }

        public PspUserResponseBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public PspUserResponseBuilder role(String role) {
            this.role = role;
            return this;
        }

        public PspUserResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public PspUserResponse build() {
            return new PspUserResponse(id, email, fullName, role, status);
        }

        public String toString() {
            return "PspUserResponse.PspUserResponseBuilder(id=" + this.id + ", email=" + this.email + ", fullName="
                    + this.fullName + ", role=" + this.role + ", status=" + this.status + ")";
        }
    }
}
