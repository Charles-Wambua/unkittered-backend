-- "Get vouched for": a member shares a short single-use code; a verified
-- member who knows them enters it and confirms. One vouch earns the badge.
CREATE TABLE vouch_invites (
    code        VARCHAR(12)  PRIMARY KEY,
    inviter_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_by     UUID         REFERENCES users (id) ON DELETE SET NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_vouch_invites_inviter ON vouch_invites (inviter_id);

CREATE TABLE vouches (
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    voucher_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, voucher_id),
    CHECK (user_id <> voucher_id)
);
