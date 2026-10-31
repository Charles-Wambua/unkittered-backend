-- Announcements sent from the admin dashboard (push and/or email), kept as a
-- history of what went to whom and when.
CREATE TABLE admin_broadcasts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sent_by          UUID REFERENCES users(id) ON DELETE SET NULL,
    title            VARCHAR(80)  NOT NULL,
    body             VARCHAR(500) NOT NULL,
    audience         VARCHAR(24)  NOT NULL,
    via_push         BOOLEAN      NOT NULL,
    via_email        BOOLEAN      NOT NULL,
    recipients       INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_broadcasts_created ON admin_broadcasts(created_at DESC);
