package com.posgateway.aml.entity.mobilemoney;

import com.posgateway.aml.entity.multiasset.AssetAccount;
import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
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
@Table(name = "mobile_money_transaction_contexts", uniqueConstraints =
        @UniqueConstraint(name = "uq_mobile_money_transaction_context", columnNames = {"psp_id", "transaction_id"}))
@Getter
@Setter
public class MobileMoneyTransactionContext {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private MultiAssetTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_account_id")
    private AssetAccount walletAccount;

    @Column(name = "wallet_reference", nullable = false, length = 160)
    private String walletReference;

    @Column(name = "counterparty_wallet_reference", length = 160)
    private String counterpartyWalletReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private MobileMoneyEventType eventType;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "agent_device_fingerprint")
    private String agentDeviceFingerprint;

    @Column(name = "sim_identifier_hash")
    private String simIdentifierHash;

    @Column(name = "agent_reference", length = 160)
    private String agentReference;

    @Column(name = "merchant_reference", length = 160)
    private String merchantReference;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "agent_latitude", precision = 10, scale = 7)
    private BigDecimal agentLatitude;

    @Column(name = "agent_longitude", precision = 10, scale = 7)
    private BigDecimal agentLongitude;

    @Column(name = "cell_id", length = 128)
    private String cellId;

    @Column(name = "sim_changed_at")
    private LocalDateTime simChangedAt;

    @Column(name = "device_registered_at")
    private LocalDateTime deviceRegisteredAt;

    @Column(name = "wallet_previous_activity_at")
    private LocalDateTime walletPreviousActivityAt;

    @Column(name = "agent_float_before", precision = 24, scale = 8)
    private BigDecimal agentFloatBefore;

    @Column(name = "agent_float_after", precision = 24, scale = 8)
    private BigDecimal agentFloatAfter;

    @Column(name = "cross_border", nullable = false)
    private boolean crossBorder;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
