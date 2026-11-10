-- The official Unkittered account (the team's own profile): shown with the
-- verified tick everywhere and kept out of the discover deck and map.
ALTER TABLE profiles ADD COLUMN is_official BOOLEAN NOT NULL DEFAULT false;
