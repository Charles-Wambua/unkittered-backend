-- Moderation: reports filed against meetups.
CREATE TABLE meetup_reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meetup_id   UUID NOT NULL REFERENCES meetups(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    reason      TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meetup_reports_meetup ON meetup_reports(meetup_id);
