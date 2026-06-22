-- Connection modes: what each member is on Unkittered for.
--   connection_mode ∈ ('dating','friends','both')
--   open_to_friends is a denormalised convenience flag (mode <> 'dating'),
--   used purely to render the 🤝 badge without parsing the mode client-side.
ALTER TABLE profiles
    ADD COLUMN connection_mode VARCHAR(16) NOT NULL DEFAULT 'dating';
ALTER TABLE profiles
    ADD COLUMN open_to_friends BOOLEAN NOT NULL DEFAULT false;

-- Existing members (seeds + early adopters) become "open to both" so the
-- Friends deck is populated from day one. New sign-ups default to 'dating'.
UPDATE profiles SET connection_mode = 'both', open_to_friends = true;
