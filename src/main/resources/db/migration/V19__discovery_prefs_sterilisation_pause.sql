-- ─────────────────────────────────────────────────────────────────────────────
--   • discovery prefs      — who a member wants to see (age, distance, badges…)
--   • sterilisation badge  — self-declared procedure, verified by medical review
--   • pause                — hide a profile from every deck without deleting it
--   • more pack kinds      — dinners and trips
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE discovery_prefs (
    user_id          UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    age_min          INT     NOT NULL DEFAULT 18 CHECK (age_min BETWEEN 18 AND 99),
    age_max          INT     NOT NULL DEFAULT 99 CHECK (age_max BETWEEN 18 AND 99),
    max_distance_km  INT              CHECK (max_distance_km BETWEEN 1 AND 500),
    verified_only    BOOLEAN NOT NULL DEFAULT false,
    sterilised_only  BOOLEAN NOT NULL DEFAULT false,
    has_pets_only    BOOLEAN NOT NULL DEFAULT false,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (age_min <= age_max)
);

-- Only show people with at least one of these species (empty = any).
CREATE TABLE discovery_pref_species (
    user_id UUID NOT NULL REFERENCES discovery_prefs(user_id) ON DELETE CASCADE,
    value   TEXT NOT NULL CHECK (value IN ('dog', 'cat', 'bird', 'reptile', 'other')),
    PRIMARY KEY (user_id, value)
);

-- Only show people sharing at least one of these interests (empty = any).
CREATE TABLE discovery_pref_interests (
    user_id UUID NOT NULL REFERENCES discovery_prefs(user_id) ON DELETE CASCADE,
    value   TEXT NOT NULL,
    PRIMARY KEY (user_id, value)
);

ALTER TABLE profiles
    ADD COLUMN paused                  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN sterilisation_procedure TEXT
        CHECK (sterilisation_procedure IN
               ('vasectomy', 'bilateral_tubal_ligation', 'salpingectomy', 'hysterectomy', 'other')),
    ADD COLUMN sterilisation_verified  BOOLEAN NOT NULL DEFAULT false;

-- Medical proof for the verified sterilisation badge. Reviewed by a person;
-- the document link is cleared once reviewed.
CREATE TABLE sterilisation_proofs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    document_url  TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at   TIMESTAMPTZ
);
CREATE INDEX idx_sterilisation_proofs_status ON sterilisation_proofs(status);

ALTER TABLE meetups DROP CONSTRAINT IF EXISTS meetups_kind_check;
ALTER TABLE meetups ADD CONSTRAINT meetups_kind_check
    CHECK (kind IN ('walk', 'hike', 'picnic', 'cafe', 'dinner', 'travel', 'games', 'volunteer'));
