package com.posgateway.aml.entity.messaging;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Internal message / notification delivered to a platform user.
 *
 * <p>Backs the {@code /api/v1/messages} endpoints in
 * {@link com.posgateway.aml.controller.MessagesController}. A {@code null}
 * {@code senderUserId} indicates a system-generated message.
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_recipient_created",
                columnList = "recipient_user_id, created_at DESC"),
        @Index(name = "idx_messages_recipient_unread",
                columnList = "recipient_user_id, read_at"),
        @Index(name = "idx_messages_related",
                columnList = "related_entity_type, related_entity_id")
})
public class Message {

    public enum Category { SYSTEM, ALERT, COMPLIANCE, CASE, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK -> platform_users(id). The user who receives this message. */
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    /** FK -> platform_users(id), nullable. NULL = system-generated. */
    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private Category category = Category.OTHER;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Optional polymorphic link, e.g. "case", "alert". */
    @Column(name = "related_entity_type", length = 64)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    public Message() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long v) { this.recipientUserId = v; }

    public Long getSenderUserId() { return senderUserId; }
    public void setSenderUserId(Long v) { this.senderUserId = v; }

    public String getSubject() { return subject; }
    public void setSubject(String v) { this.subject = v; }

    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }

    public Category getCategory() { return category; }
    public void setCategory(Category v) { this.category = v; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime v) { this.readAt = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public String getRelatedEntityType() { return relatedEntityType; }
    public void setRelatedEntityType(String v) { this.relatedEntityType = v; }

    public Long getRelatedEntityId() { return relatedEntityId; }
    public void setRelatedEntityId(Long v) { this.relatedEntityId = v; }
}
