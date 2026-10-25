-- ─────────────────────────────────────────────────────────────────────────────
-- Replies in 1:1 chats, and a group chat per pack (meetup).
-- ─────────────────────────────────────────────────────────────────────────────

-- Swipe-to-reply: the message being answered. If that message is ever
-- deleted, the reply stays and just loses its quote.
ALTER TABLE messages ADD COLUMN reply_to_id UUID REFERENCES messages(id) ON DELETE SET NULL;

-- Pack chat: everyone who RSVP'd (the host included) can post from 24h before
-- the pack starts until 24h after it ends.
CREATE TABLE meetup_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meetup_id   UUID NOT NULL REFERENCES meetups(id) ON DELETE CASCADE,
    sender_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body        TEXT NOT NULL,
    reply_to_id UUID REFERENCES meetup_messages(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_meetup_messages_meetup ON meetup_messages(meetup_id, created_at);

-- Per-member read marker for the pack chat's unread count.
ALTER TABLE meetup_rsvps ADD COLUMN chat_read_at TIMESTAMPTZ;
