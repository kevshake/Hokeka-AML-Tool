package com.posgateway.aml.entity.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.posgateway.aml.entity.psp.Psp;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "psp_payment_methods")
public class PspPaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_method_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "psp_id", nullable = false)
    private Psp psp;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "token_vault_ref", nullable = false, length = 512)
    private String tokenVaultRef;
    @Column(name = "last4", nullable = false, length = 4)
    private String last4;
    @Column(name = "brand", nullable = false, length = 32)
    private String brand;
    @Column(name = "expiry_month", nullable = false)
    private Integer expiryMonth;
    @Column(name = "expiry_year", nullable = false)
    private Integer expiryYear;
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Psp getPsp() { return psp; }
    public void setPsp(Psp value) { psp = value; }
    public String getTokenVaultRef() { return tokenVaultRef; }
    public void setTokenVaultRef(String value) { tokenVaultRef = value; }
    public String getLast4() { return last4; }
    public void setLast4(String value) { last4 = value; }
    public String getBrand() { return brand; }
    public void setBrand(String value) { brand = value; }
    public Integer getExpiryMonth() { return expiryMonth; }
    public void setExpiryMonth(Integer value) { expiryMonth = value; }
    public Integer getExpiryYear() { return expiryYear; }
    public void setExpiryYear(Integer value) { expiryYear = value; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean value) { active = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    @PreUpdate void updateTimestamp() { updatedAt = LocalDateTime.now(); }
}
