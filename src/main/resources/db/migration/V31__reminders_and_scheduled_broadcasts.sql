-- Re-engagement reminders, per-member quiet hours and scheduled announcements.

-- "Reminders & tips" (nudges about unanswered chats, new people, the profile).
ALTER TABLE notification_prefs ADD COLUMN tips BOOLEAN NOT NULL DEFAULT true;

-- The phone's offset from UTC, so reminders arrive in daytime where the member is.
ALTER TABLE users ADD COLUMN utc_offset_minutes INT;

-- Every reminder and announcement sent, for de-duplication, frequency caps and
-- open rates. {kind, ref} identify what it was about (e.g. unread + match id).
CREATE TABLE notification_log (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind       VARCHAR(32) NOT NULL,
    ref        TEXT NOT NULL DEFAULT '',
    channel    VARCHAR(8)  NOT NULL DEFAULT 'push',
    sent_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    opened_at  TIMESTAMPTZ
);
CREATE INDEX idx_notification_log_user_sent ON notification_log(user_id, sent_at DESC);
CREATE INDEX idx_notification_log_kind_ref  ON notification_log(kind, ref);

-- Announcements can be scheduled for a local time (e.g. 25 Dec 09:00 wherever
-- each member is) and delivered as that hour arrives around the world.
ALTER TABLE admin_broadcasts
    ADD COLUMN status        VARCHAR(12) NOT NULL DEFAULT 'sent'
        CHECK (status IN ('sent', 'scheduled', 'sending', 'cancelled')),
    ADD COLUMN send_at_local TIMESTAMP,
    ADD COLUMN completed_at  TIMESTAMPTZ;
