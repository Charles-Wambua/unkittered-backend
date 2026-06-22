-- FCM device registration tokens (one user may have several devices).
CREATE TABLE device_tokens (
    token       VARCHAR(512) PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform    VARCHAR(16)  NOT NULL DEFAULT 'unknown',  -- android | ios | web
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);

-- Track which trip/meetup reminders we've already pushed, so the scheduler
-- never double-sends.
ALTER TABLE meetup_rsvps
    ADD COLUMN reminded_24h BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE meetup_rsvps
    ADD COLUMN reminded_1h  BOOLEAN NOT NULL DEFAULT false;
