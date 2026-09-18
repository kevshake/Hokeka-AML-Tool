package com.posgateway.aml.entity.market;

import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
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
@Table(name = "market_orders", uniqueConstraints =
        @UniqueConstraint(name = "uq_market_order_external", columnNames = {"psp_id", "external_order_id"}))
@Getter
@Setter
public class MarketOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "psp_id", nullable = false)
    private Long pspId;
    @Column(name = "external_order_id", nullable = false, length = 128)
    private String externalOrderId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;
    @Column(name = "account_reference", nullable = false, length = 128)
    private String accountReference;
    @Column(name = "beneficial_owner_reference", length = 128)
    private String beneficialOwnerReference;
    @Column(name = "instrument_id", nullable = false, length = 128)
    private String instrumentId;
    @Column(length = 64)
    private String symbol;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private MarketOrderSide side;
    @Column(name = "order_type", nullable = false, length = 24)
    private String orderType;
    @Column(nullable = false, precision = 28, scale = 10)
    private BigDecimal quantity;
    @Column(name = "executed_quantity", nullable = false, precision = 28, scale = 10)
    private BigDecimal executedQuantity = BigDecimal.ZERO;
    @Column(name = "limit_price", precision = 28, scale = 10)
    private BigDecimal limitPrice;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(length = 64)
    private String venue;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private MarketOrderStatus status = MarketOrderStatus.OPEN;
    @Column(name = "placed_at", nullable = false)
    private LocalDateTime placedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    @Column(name = "market_close_at")
    private LocalDateTime marketCloseAt;
    @Column(name = "reference_price", precision = 28, scale = 10)
    private BigDecimal referencePrice;
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
