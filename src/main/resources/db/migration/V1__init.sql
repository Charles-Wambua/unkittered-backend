-- ─────────────────────────────────────────────────────────────────────────────
-- Unkittered initial schema
-- ─────────────────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Account / identity (maps to Flutter AuthUser)
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) UNIQUE,
    phone               VARCHAR(32)  UNIQUE,
    password_hash       VARCHAR(255),
    display_name        VARCHAR(120) NOT NULL DEFAULT '',
    onboarding_complete BOOLEAN      NOT NULL DEFAULT FALSE,
    subscription_tier   VARCHAR(16)  NOT NULL DEFAULT 'free',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Public dating profile (maps to Flutter Profile). Shares PK with users (1:1).
CREATE TABLE profiles (
    user_id              UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    name                 VARCHAR(120) NOT NULL DEFAULT '',
    age                  INT          NOT NULL DEFAULT 18,
    image_url            TEXT         NOT NULL DEFAULT '',
    bio                  TEXT         NOT NULL DEFAULT '',
    location             VARCHAR(160) NOT NULL DEFAULT '',
    latitude             DOUBLE PRECISION,
    longitude            DOUBLE PRECISION,
    is_verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    occupation           VARCHAR(160) NOT NULL DEFAULT '',
    education            VARCHAR(160) NOT NULL DEFAULT '',
    child_free_statement VARCHAR(255) NOT NULL DEFAULT 'Intentionally child-free',
    card_quote           VARCHAR(255) NOT NULL DEFAULT '',
    is_online            BOOLEAN      NOT NULL DEFAULT FALSE,
    last_active_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- List-valued profile attributes (ElementCollection tables)
CREATE TABLE profile_interests (
    user_id UUID NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    value   VARCHAR(80) NOT NULL
);
CREATE TABLE profile_gallery (
    user_id UUID NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    value   TEXT NOT NULL
);
CREATE TABLE profile_pets (
    user_id UUID NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    value   VARCHAR(120) NOT NULL
);
CREATE TABLE profile_lifestyle_tags (
    user_id UUID NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    value   VARCHAR(120) NOT NULL
);

CREATE INDEX idx_profile_interests_user ON profile_interests(user_id);
CREATE INDEX idx_profile_gallery_user   ON profile_gallery(user_id);
CREATE INDEX idx_profile_pets_user      ON profile_pets(user_id);
CREATE INDEX idx_profile_lifestyle_user ON profile_lifestyle_tags(user_id);

-- Likes & super-likes
CREATE TABLE likes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    liker_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    likee_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    super_like BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_like UNIQUE (liker_id, likee_id),
    CONSTRAINT chk_like_not_self CHECK (liker_id <> likee_id)
);
CREATE INDEX idx_likes_likee ON likes(likee_id);
CREATE INDEX idx_likes_liker ON likes(liker_id);

-- Passes (skips)
CREATE TABLE passes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passer_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    passee_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pass UNIQUE (passer_id, passee_id)
);
CREATE INDEX idx_passes_passer ON passes(passer_id);

-- Matches (mutual likes). user_low < user_high keeps the pair canonical.
CREATE TABLE matches (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_low   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_high  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_match UNIQUE (user_low, user_high),
    CONSTRAINT chk_match_order CHECK (user_low < user_high)
);
CREATE INDEX idx_matches_low  ON matches(user_low);
CREATE INDEX idx_matches_high ON matches(user_high);
