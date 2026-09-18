package com.posgateway.aml.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "virtual_asset_regulator_access_grants") @Getter @Setter
public class VirtualAssetRegulatorAccessGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @Column(name = "regulator_name", nullable = false) private String regulatorName;
    @Column(nullable = false, length = 3) private String jurisdiction;
    @Column(name = "access_key_hash", nullable = false, unique = true, length = 64) private String accessKeyHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private List<String> scopes = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "allowed_ip_addresses", columnDefinition = "jsonb", nullable = false)
    private List<String> allowedIpAddresses = new ArrayList<>();
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Column(name = "last_accessed_at") private LocalDateTime lastAccessedAt;
    @Column(name = "created_by", nullable = false, length = 160) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
