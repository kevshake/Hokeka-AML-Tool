package com.posgateway.aml.entity.market;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.multiasset.FinancialCrimeSignalType;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "market_surveillance_signals")
@Getter
@Setter
public class MarketSurveillanceSignal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "psp_id", nullable = false)
    private Long pspId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id")
    private MarketOrder order;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "execution_id")
    private MarketExecution execution;
    @Column(name = "scenario_code", nullable = false, length = 64)
    private String scenarioCode;
    @Enumerated(EnumType.STRING) @Column(name = "signal_type", nullable = false, length = 32)
    private FinancialCrimeSignalType signalType = FinancialCrimeSignalType.MARKET_ABUSE;
    @Column(nullable = false, length = 16)
    private String severity;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidence = new LinkedHashMap<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private MarketSignalStatus status = MarketSignalStatus.OPEN;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by")
    private User reviewedBy;
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
