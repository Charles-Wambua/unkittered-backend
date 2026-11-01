-- When a paid membership ends (NULL = doesn't lapse on its own, e.g. granted by
-- an admin). An hourly job moves lapsed members back to free.
ALTER TABLE users ADD COLUMN subscription_expires_at TIMESTAMPTZ;

-- Sign-ins issued before this moment no longer work (set by a password reset),
-- so a stolen session can't outlive the password it was taken with.
ALTER TABLE users ADD COLUMN tokens_valid_after TIMESTAMPTZ;
