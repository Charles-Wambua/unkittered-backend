-- ─────────────────────────────────────────────────────────────────────────────
-- Safety: blocks & reports. (Unmatch reuses the existing matches/messages tables.)
-- ─────────────────────────────────────────────────────────────────────────────

-- A blocks B: B vanishes from A's deck/matches and neither can reach the other.
CREATE TABLE blocks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_block UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_block_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_blocks_blocker ON blocks(blocker_id);
CREATE INDEX idx_blocks_blocked ON blocks(blocked_id);

-- User reports for moderation review.
CREATE TABLE reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason      VARCHAR(64)  NOT NULL,
    details     VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_report_not_self CHECK (reporter_id <> reported_id)
);
CREATE INDEX idx_reports_reported ON reports(reported_id);
