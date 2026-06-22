-- Admin role for moderation / dashboard access.
ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;

-- Promote an admin by email, e.g.:
--   UPDATE users SET is_admin = true WHERE email = 'you@example.com';
