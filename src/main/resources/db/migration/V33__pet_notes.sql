-- The pet note left with a super-like ("from Biscuit to Luna: ..."). It becomes
-- the sender's first message in the chat once the two match.
ALTER TABLE likes
    ADD COLUMN note          VARCHAR(300),
    ADD COLUMN note_from_pet VARCHAR(30),
    ADD COLUMN note_to_pet   VARCHAR(30);
