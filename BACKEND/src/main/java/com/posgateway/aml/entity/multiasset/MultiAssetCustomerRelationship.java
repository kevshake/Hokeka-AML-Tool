package com.posgateway.aml.entity.multiasset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "multi_asset_customer_relationships", uniqueConstraints = @UniqueConstraint(
        name = "uq_multi_asset_customer_relationship",
        columnNames = {"psp_id", "customer_id", "related_customer_id", "relationship_type"}))
@Getter
@Setter
public class MultiAssetCustomerRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "related_customer_id", nullable = false)
    private MultiAssetCustomer relatedCustomer;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 32)
    private CustomerRelationshipType relationshipType;

    @Column(name = "ownership_percentage", precision = 7, scale = 4)
    private BigDecimal ownershipPercentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}

