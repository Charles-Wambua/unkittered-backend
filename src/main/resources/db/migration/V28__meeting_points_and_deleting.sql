-- Packs: a host-set meeting point, "I'm here" check-ins and deletable messages.
ALTER TABLE meetups ADD COLUMN meeting_point VARCHAR(140);
ALTER TABLE meetup_messages
    ADD COLUMN kind       VARCHAR(16) NOT NULL DEFAULT 'text',
    ADD COLUMN deleted_at TIMESTAMPTZ;

-- 1:1 chats: Gold members can delete their messages (for everyone) and clear
-- a conversation's history for themselves.
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE TABLE chat_clears (
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    match_id   UUID        NOT NULL,
    cleared_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, match_id)
);
