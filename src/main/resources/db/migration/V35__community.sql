-- Phase 2 and 3 of the official account: badges, Pet of the week, the
-- in-app update banner and which app version each member runs.

-- Badges Shenron hands out (early_tester, verified, first_match, first_pack, pet_of_week).
CREATE TABLE member_badges (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code       VARCHAR(32) NOT NULL,
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, code)
);

-- One nomination per member per week (Monday, Nairobi time); changing it replaces it.
CREATE TABLE pet_nominations (
    week         DATE        NOT NULL,
    nominator_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pet_id       UUID        NOT NULL REFERENCES pets (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (week, nominator_id)
);
CREATE INDEX pet_nominations_pet ON pet_nominations (week, pet_id);

-- The winners, one per week. Pet details are copied so a deleted pet doesn't
-- erase the history.
CREATE TABLE pets_of_the_week (
    week        DATE         PRIMARY KEY,
    pet_id      UUID,
    owner_id    UUID         REFERENCES users (id) ON DELETE SET NULL,
    pet_name    VARCHAR(80)  NOT NULL,
    pet_photo   TEXT,
    owner_name  VARCHAR(120) NOT NULL,
    nominations INT          NOT NULL,
    picked_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The latest app build members should run (a single row).
CREATE TABLE app_release (
    id         INT          PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    version    VARCHAR(20)  NOT NULL,
    build      INT          NOT NULL,
    url        TEXT         NOT NULL,
    notes      TEXT         NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The app reports its version on every request (from 1.0.4).
ALTER TABLE users ADD COLUMN app_version VARCHAR(20);
ALTER TABLE users ADD COLUMN app_version_seen_at TIMESTAMPTZ;
