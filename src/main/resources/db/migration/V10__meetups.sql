-- ─────────────────────────────────────────────────────────────────────────────
-- Meetups: child-free group events, and their RSVPs.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE meetups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       TEXT NOT NULL,
    category    TEXT NOT NULL,
    area        TEXT NOT NULL,
    venue       TEXT NOT NULL DEFAULT '',
    starts_at   TIMESTAMPTZ,
    capacity    INT  NOT NULL DEFAULT 0,
    description TEXT NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meetups_starts ON meetups(starts_at);
CREATE INDEX idx_meetups_host   ON meetups(host_id);

CREATE TABLE meetup_rsvps (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meetup_id  UUID NOT NULL REFERENCES meetups(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rsvp UNIQUE (meetup_id, user_id)
);

CREATE INDEX idx_rsvps_meetup ON meetup_rsvps(meetup_id);
CREATE INDEX idx_rsvps_user   ON meetup_rsvps(user_id);
