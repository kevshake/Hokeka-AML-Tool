package com.posgateway.aml.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity @Immutable @Table(name = "vasp_screening_records") @Getter @Setter
public class VaspScreeningRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vasp_id", nullable = false)
    private VaspDirectoryEntry vasp;
    @Column(name = "subject_name", nullable = false) private String subjectName;
    @Column(name = "subject_type", nullable = false, length = 32) private String subjectType;
    @Column(nullable = false, length = 128) private String provider;
    @Column(nullable = false) private boolean available;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "match_count", nullable = false) private int matchCount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> matches = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> evidence = new LinkedHashMap<>();
    @Column(name = "screened_at", nullable = false, updatable = false) private LocalDateTime screenedAt;
    @Column(name = "retain_until", nullable = false, updatable = false) private LocalDate retainUntil;
    @PrePersist void create() { if (screenedAt == null) screenedAt = LocalDateTime.now(); if (retainUntil == null) retainUntil = screenedAt.toLocalDate().plusYears(7); }
}
