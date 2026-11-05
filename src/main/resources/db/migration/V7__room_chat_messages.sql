ALTER TABLE chat_participant
    ADD COLUMN visible_after_message_id BIGINT NOT NULL DEFAULT 0;

-- Preserve the visibility boundary for memberships that predate this migration.
UPDATE chat_participant p
SET visible_after_message_id = COALESCE((
    SELECT MAX(m.id) FROM chat_message m
    WHERE m.chat_thread_id = p.chat_thread_id AND m.created_at < p.joined_at
), 0);

ALTER TABLE chat_message
    ADD COLUMN client_message_id VARCHAR(36),
    ADD CONSTRAINT uq_msg_client UNIQUE (chat_thread_id, sender_id, client_message_id),
    ADD INDEX idx_msg_thread_id (chat_thread_id, id);
