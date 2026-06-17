-- Privacy: keep exact distance off the profile as shown to other users.
ALTER TABLE profiles
    ADD COLUMN hide_distance BOOLEAN NOT NULL DEFAULT FALSE;
