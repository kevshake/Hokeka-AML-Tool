package com.posgateway.aml.entity.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.posgateway.aml.entity.psp.Psp;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "psp_api_keys")
public class PspApiKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "api_key_id")
    private Long id;
    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;
    @JsonIgnore
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "psp_id", nullable = false)
    @JsonIgnore
    private Psp psp;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public Long getId() { return id; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String value) { keyPrefix = value; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String value) { keyHash = value; }
    public Psp getPsp() { return psp; }
    public void setPsp(Psp value) { psp = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(LocalDateTime value) { rotatedAt = value; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime value) { revokedAt = value; }
}
