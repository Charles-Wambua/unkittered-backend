-- ─────────────────────────────────────────────────────────────────────────────
-- Reels (video profiles) and Stories (ephemeral 24h frames).
-- Media bytes live in the storage module (local disk for self-hosting); these
-- tables only hold the resulting URLs + timestamps.
-- ─────────────────────────────────────────────────────────────────────────────

-- Video profiles. A user may upload several over time; the feed surfaces the
-- most recent per person as their active reel.
CREATE TABLE reels (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_url  TEXT NOT NULL,
    poster_url TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reels_user_created ON reels(user_id, created_at DESC);
CREATE INDEX idx_reels_created      ON reels(created_at DESC);

-- Story frames. Expiry (>24h) is applied at query time so there's no sweeper to
-- run; a periodic cleanup can prune old rows later if desired.
CREATE TABLE stories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_url  TEXT,
    body       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stories_user_created ON stories(user_id, created_at DESC);
CREATE INDEX idx_stories_created      ON stories(created_at DESC);
