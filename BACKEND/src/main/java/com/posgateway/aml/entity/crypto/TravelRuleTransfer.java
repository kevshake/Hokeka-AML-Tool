package com.posgateway.aml.entity.crypto;

import com.posgateway.aml.entity.converter.AesGcmStringConverter;
import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "travel_rule_transfers", uniqueConstraints = @UniqueConstraint(
        name = "uq_travel_rule_transfer", columnNames = {"psp_id", "transaction_id"})) @Getter @Setter
public class TravelRuleTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id", nullable = false)
    private MultiAssetTransaction transaction;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private TravelRuleJurisdictionPolicy policy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "originator_vasp_id") private VaspDirectoryEntry originatorVasp;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "beneficiary_vasp_id") private VaspDirectoryEntry beneficiaryVasp;
    @Column(nullable = false, length = 3) private String jurisdiction;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private TravelRuleTransferStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "originator_verification", nullable = false, length = 24)
    private IdentityVerificationStatus originatorVerification = IdentityVerificationStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(name = "beneficiary_verification", nullable = false, length = 24)
    private IdentityVerificationStatus beneficiaryVerification = IdentityVerificationStatus.PENDING;
    @Column(name = "originator_verification_reference", length = 500) private String originatorVerificationReference;
    @Column(name = "originator_verified_by", length = 160) private String originatorVerifiedBy;
    @Column(name = "originator_verified_at") private LocalDateTime originatorVerifiedAt;
    @Column(name = "beneficiary_verification_reference", length = 500) private String beneficiaryVerificationReference;
    @Column(name = "beneficiary_verified_by", length = 160) private String beneficiaryVerifiedBy;
    @Column(name = "beneficiary_verified_at") private LocalDateTime beneficiaryVerifiedAt;
    @Column(length = 64) private String protocol;
    @Convert(converter = AesGcmStringConverter.class) @Column(name = "encrypted_payload", columnDefinition = "TEXT")
    private String encryptedPayload;
    @Column(name = "payload_hash", length = 64) private String payloadHash;
    @Column(name = "provider_message_id") private String providerMessageId;
    @Column(name = "transmission_attempts", nullable = false) private int transmissionAttempts;
    @Column(name = "transmitted_at") private LocalDateTime transmittedAt;
    @Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;
    @Column(name = "failure_reason", columnDefinition = "TEXT") private String failureReason;
    @Column(name = "retain_until", nullable = false) private LocalDate retainUntil;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (retainUntil == null) retainUntil = now.toLocalDate().plusYears(7); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
