-- Per-user, per-type push notification preferences. Absence of a row means
-- "all on" (the default), so PushService treats a missing row as all-true.
CREATE TABLE notification_prefs (
    user_id    UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    matches    BOOLEAN NOT NULL DEFAULT true,
    messages   BOOLEAN NOT NULL DEFAULT true,
    reminders  BOOLEAN NOT NULL DEFAULT true,
    likes      BOOLEAN NOT NULL DEFAULT true
);
