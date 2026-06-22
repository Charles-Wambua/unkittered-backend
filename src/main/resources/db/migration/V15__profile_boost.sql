-- Profile Boost: while boosted_until is in the future, the profile is surfaced
-- to the top of others' discover decks. Set by POST /v1/subscriptions/boost
-- and naturally lapses (no sweep needed — the deck query compares against now).
ALTER TABLE profiles
    ADD COLUMN boosted_until TIMESTAMPTZ;
