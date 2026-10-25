-- Editing, emoji reactions (1:1 and pack chats) and polls in pack chats.
ALTER TABLE messages ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE meetup_messages
    ADD COLUMN edited_at TIMESTAMPTZ,
    -- Polls: {"options": [...], "closed": false}.
    ADD COLUMN payload JSONB;

-- One reaction per person per message; picking another replaces it.
CREATE TABLE message_reactions (
    message_id UUID        NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji      VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
CREATE TABLE meetup_message_reactions (
    message_id UUID        NOT NULL REFERENCES meetup_messages(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji      VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);

-- One vote per member per poll; voting again changes it.
CREATE TABLE meetup_poll_votes (
    message_id   UUID        NOT NULL REFERENCES meetup_messages(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    option_index INT         NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
