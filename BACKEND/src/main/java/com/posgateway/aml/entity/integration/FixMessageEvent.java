package com.posgateway.aml.entity.integration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "fix_message_events", indexes = {
        @Index(name = "idx_fix_message_psp_received", columnList = "psp_id,received_at"),
        @Index(name = "idx_fix_message_session_sequence", columnList = "session_id,message_sequence_number"),
        @Index(name = "idx_fix_message_business_reference", columnList = "psp_id,business_reference")
}, uniqueConstraints = @UniqueConstraint(
        name = "uq_fix_message_session_sequence_direction",
        columnNames = {"session_id", "message_sequence_number", "direction"}))
@Getter
@Setter
public class FixMessageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(nullable = false, length = 16)
    private String direction;

    @Column(name = "message_type", nullable = false, length = 16)
    private String messageType;

    @Column(name = "message_sequence_number", nullable = false)
    private int messageSequenceNumber;

    @Column(name = "sending_time")
    private LocalDateTime sendingTime;

    @Column(name = "business_reference", length = 160)
    private String businessReference;

    @Column(name = "message_hash", nullable = false, length = 64)
    private String messageHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanitized_fields", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sanitizedFields = new LinkedHashMap<>();

    @Column(nullable = false, length = 24)
    private String outcome;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "market_order_id")
    private Long marketOrderId;

    @Column(name = "market_execution_id")
    private Long marketExecutionId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void create() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
        if (outcome == null) outcome = "RECEIVED";
    }
}
