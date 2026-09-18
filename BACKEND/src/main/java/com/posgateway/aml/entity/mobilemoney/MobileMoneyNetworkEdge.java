package com.posgateway.aml.entity.mobilemoney;

import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_money_network_edges", uniqueConstraints = @UniqueConstraint(
        name = "uq_mobile_money_network_edge",
        columnNames = {"psp_id", "from_entity_type", "from_entity_reference", "to_entity_type",
                "to_entity_reference", "relationship_type"}))
@Getter
@Setter
public class MobileMoneyNetworkEdge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_entity_type", nullable = false, length = 32)
    private MobileMoneyEntityType fromEntityType;

    @Column(name = "from_entity_reference", nullable = false)
    private String fromEntityReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_entity_type", nullable = false, length = 32)
    private MobileMoneyEntityType toEntityType;

    @Column(name = "to_entity_reference", nullable = false)
    private String toEntityReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 40)
    private MobileMoneyRelationshipType relationshipType;

    @Column(name = "occurrence_count", nullable = false)
    private long occurrenceCount = 1;

    @Column(name = "cumulative_amount", nullable = false, precision = 24, scale = 8)
    private BigDecimal cumulativeAmount = BigDecimal.ZERO;

    @Column(length = 12)
    private String currency;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_transaction_id")
    private MultiAssetTransaction latestTransaction;

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
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
