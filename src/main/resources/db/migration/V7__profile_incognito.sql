-- Privacy (Gold): browse privately. Incognito users are hidden from others'
-- discover decks, but still visible to anyone they've already liked.
ALTER TABLE profiles
    ADD COLUMN incognito BOOLEAN NOT NULL DEFAULT FALSE;
