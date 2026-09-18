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
@Table(name = "market_executions", uniqueConstraints =
        @UniqueConstraint(name = "uq_market_execution_external", columnNames = {"psp_id", "external_execution_id"}))
@Getter
@Setter
public class MarketExecution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "psp_id", nullable = false)
    private Long pspId;
    @Column(name = "external_execution_id", nullable = false, length = 128)
    private String externalExecutionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    private MarketOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;
    @Column(name = "instrument_id", nullable = false, length = 128)
    private String instrumentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private MarketOrderSide side;
    @Column(nullable = false, precision = 28, scale = 10)
    private BigDecimal quantity;
    @Column(nullable = false, precision = 28, scale = 10)
    private BigDecimal price;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counterparty_customer_id")
    private MultiAssetCustomer counterpartyCustomer;
    @Column(name = "counterparty_reference")
    private String counterpartyReference;
    @Column(name = "counterparty_beneficial_owner_reference", length = 128)
    private String counterpartyBeneficialOwnerReference;
    @Column(length = 64)
    private String venue;
    @Column(name = "market_close_at")
    private LocalDateTime marketCloseAt;
    @Column(name = "reference_price", precision = 28, scale = 10)
    private BigDecimal referencePrice;
    @Column(name = "settlement_account_reference")
    private String settlementAccountReference;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
