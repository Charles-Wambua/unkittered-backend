-- ─────────────────────────────────────────────────────────────────────────────
-- Chat messages. Each message belongs to a match (the conversation).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id   UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    sender_id  UUID NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

-- Fetch a conversation's messages in order, and compute unread counts.
CREATE INDEX idx_messages_match_created ON messages(match_id, created_at);
CREATE INDEX idx_messages_unread        ON messages(match_id, sender_id) WHERE read_at IS NULL;
