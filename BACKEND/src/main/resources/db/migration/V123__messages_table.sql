-- ============================================================
-- V123: messages
--
-- Backs com.posgateway.aml.entity.messaging.Message and powers
-- the /api/v1/messages endpoints in MessagesController.
-- Replaces the controller's previous Collections.emptyList() and
-- Map.of("count", 0L) hardcoded responses.
--
-- Recipient/sender FKs reference platform_users(id) (V14).
-- Indexed primarily for the recipient mailbox view (most-recent
-- first) and the unread-count query.
--
-- Entity is NOT @Audited; no messages_aud counterpart.
-- ============================================================

CREATE TABLE IF NOT EXISTS messages (
    id                  BIGSERIAL    PRIMARY KEY,
    recipient_user_id   BIGINT       NOT NULL,
    sender_user_id      BIGINT,
    subject             VARCHAR(255) NOT NULL,
    body                TEXT         NOT NULL,
    category            VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    read_at             TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL,
    related_entity_type VARCHAR(64),
    related_entity_id   BIGINT,
    CONSTRAINT fk_messages_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES platform_users (id),
    CONSTRAINT fk_messages_sender
        FOREIGN KEY (sender_user_id)    REFERENCES platform_users (id)
);

-- Mailbox view: ORDER BY created_at DESC scoped to a recipient.
CREATE INDEX IF NOT EXISTS idx_messages_recipient_created
    ON messages (recipient_user_id, created_at DESC);

-- Unread-only filter / count(read_at IS NULL).
CREATE INDEX IF NOT EXISTS idx_messages_recipient_unread
    ON messages (recipient_user_id, read_at);

-- Polymorphic back-reference (e.g. messages about a specific case/alert).
CREATE INDEX IF NOT EXISTS idx_messages_related
    ON messages (related_entity_type, related_entity_id);

COMMENT ON TABLE  messages IS 'Per-user internal messaging / notifications (Message entity)';
COMMENT ON COLUMN messages.sender_user_id IS 'NULL = system-generated message';
