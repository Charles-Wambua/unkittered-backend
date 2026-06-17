-- Privacy: whether a user shares their online / last-active status with others.
-- (last_active_at already exists from V1; this adds the visibility flag.)
ALTER TABLE profiles
    ADD COLUMN show_activity BOOLEAN NOT NULL DEFAULT TRUE;
