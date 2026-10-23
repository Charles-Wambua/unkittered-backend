-- Secure password reset: a short-lived, single-use code per account. Only a
-- hash is stored, and attempts are capped so the code can't be brute-forced.
CREATE TABLE password_reset_codes (
    user_id     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    code_hash   VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Verified social identities (Google / Apple), linked to an account by the
-- provider's stable subject id rather than by email alone.
CREATE TABLE user_identities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider    VARCHAR(16)  NOT NULL CHECK (provider IN ('google', 'apple')),
    subject     VARCHAR(255) NOT NULL,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email       VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE INDEX idx_user_identities_user ON user_identities (user_id);
