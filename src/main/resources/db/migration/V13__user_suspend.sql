-- Moderation: suspended users can't authenticate.
ALTER TABLE users ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT false;
