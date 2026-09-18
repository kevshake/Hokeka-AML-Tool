package com.posgateway.aml.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Immutable @Table(name = "virtual_asset_regulator_access_logs") @Getter @Setter
public class VirtualAssetRegulatorAccessLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "grant_id", nullable = false)
    private VirtualAssetRegulatorAccessGrant grant;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @Column(nullable = false) private String endpoint;
    @Column(name = "query_hash", nullable = false, length = 64) private String queryHash;
    @Column(name = "source_ip", nullable = false, length = 64) private String sourceIp;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "rows_returned", nullable = false) private int rowsReturned;
    @Column(name = "accessed_at", nullable = false, updatable = false) private LocalDateTime accessedAt;
    @Column(name = "retain_until", nullable = false, updatable = false) private LocalDate retainUntil;
    @PrePersist void create() { if (accessedAt == null) accessedAt = LocalDateTime.now(); if (retainUntil == null) retainUntil = accessedAt.toLocalDate().plusYears(7); }
}
