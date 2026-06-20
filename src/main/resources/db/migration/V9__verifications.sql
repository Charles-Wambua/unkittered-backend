-- ─────────────────────────────────────────────────────────────────────────────
-- Photo verification requests. One row per user (re-submitting replaces it).
-- Approval flips profiles.is_verified (done by a reviewer/admin), which is what
-- the verified ✓ badge and the GET status read from.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE verifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    selfie_url  TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'pending',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX idx_verifications_status ON verifications(status);
