-- ─────────────────────────────────────────────────────────────────────────────
-- First-class fields for the pet-first app:
--   • pets            — structured animals (replaces free-text profile_pets as
--                       the source of truth; profile_pets stays as a derived,
--                       read-only label list for older clients)
--   • pledge          — which child-free pledge a member took, and when
--   • rich messages   — typed chat messages (pet notes, photos, date plans)
--   • packs           — meetup kind, pet policy, duration, and RSVP plus-pets
-- ─────────────────────────────────────────────────────────────────────────────

-- Pets ───────────────────────────────────────────────────────────────────────
CREATE TABLE pets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    position        INT  NOT NULL DEFAULT 0,
    name            TEXT NOT NULL,
    species         TEXT NOT NULL DEFAULT 'dog'
        CHECK (species IN ('dog', 'cat', 'bird', 'reptile', 'other')),
    breed_and_age   TEXT NOT NULL DEFAULT '',
    energy          SMALLINT NOT NULL DEFAULT 3 CHECK (energy BETWEEN 1 AND 5),
    comes_on_dates  BOOLEAN NOT NULL DEFAULT true,
    photo_url       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pets_owner ON pets(owner_id, position);

CREATE TABLE pet_temperaments (
    pet_id UUID NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
    value  TEXT NOT NULL
        CHECK (value IN ('big_dogs', 'small_dogs', 'cats', 'new_people')),
    PRIMARY KEY (pet_id, value)
);

-- Backfill: each legacy free-text pet becomes a pet named after its label.
INSERT INTO pets (owner_id, position, name, species)
SELECT pp.user_id,
       (row_number() OVER (PARTITION BY pp.user_id ORDER BY pp.value))::int - 1,
       left(btrim(regexp_replace(pp.value, '^[^[:alnum:]]+', '')), 30),
       CASE
           WHEN pp.value LIKE '%🐕%' OR pp.value ILIKE '%dog%' THEN 'dog'
           WHEN pp.value LIKE '%🐈%' OR pp.value ILIKE '%cat%' THEN 'cat'
           WHEN pp.value LIKE '%🦜%' OR pp.value ILIKE '%bird%' OR pp.value ILIKE '%parrot%' THEN 'bird'
           ELSE 'other'
       END
FROM profile_pets pp
WHERE btrim(regexp_replace(pp.value, '^[^[:alnum:]]+', '')) <> '';

-- Pledge ─────────────────────────────────────────────────────────────────────
ALTER TABLE profiles
    ADD COLUMN pledge_path TEXT
        CHECK (pledge_path IN ('child_free_by_choice', 'sterilised', 'pet_parent')),
    ADD COLUMN pledged_at  TIMESTAMPTZ;

-- Rich messages ──────────────────────────────────────────────────────────────
-- `body` stays a human-readable rendering of every message, so the admin
-- console, push notifications and older clients keep working unchanged.
ALTER TABLE messages
    ADD COLUMN kind    TEXT NOT NULL DEFAULT 'text'
        CHECK (kind IN ('text', 'pet_note', 'photo', 'plan', 'plan_response')),
    ADD COLUMN payload JSONB;
CREATE INDEX idx_messages_match_kind ON messages(match_id, kind, created_at);

-- Packs ──────────────────────────────────────────────────────────────────────
ALTER TABLE meetups
    ADD COLUMN kind             TEXT NOT NULL DEFAULT 'cafe'
        CHECK (kind IN ('walk', 'hike', 'picnic', 'cafe', 'games', 'volunteer')),
    ADD COLUMN pet_policy       TEXT NOT NULL DEFAULT 'pets_welcome'
        CHECK (pet_policy IN ('pets_welcome', 'dogs_only', 'humans_only')),
    ADD COLUMN duration_minutes INT  NOT NULL DEFAULT 120
        CHECK (duration_minutes BETWEEN 15 AND 1440);

-- Infer a kind for existing meetups from their free-text category.
UPDATE meetups SET kind = CASE
    WHEN category ILIKE '%walk%'   THEN 'walk'
    WHEN category ILIKE '%hik%'    THEN 'hike'
    WHEN category ILIKE '%picnic%' THEN 'picnic'
    WHEN category ILIKE '%game%'   THEN 'games'
    WHEN category ILIKE '%volunt%' THEN 'volunteer'
    ELSE 'cafe'
END;

ALTER TABLE meetup_rsvps
    ADD COLUMN with_pet BOOLEAN NOT NULL DEFAULT false;
