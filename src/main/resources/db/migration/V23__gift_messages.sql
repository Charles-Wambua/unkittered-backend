-- Gifts show up in the chat as their own message kind (server-created only).
ALTER TABLE messages DROP CONSTRAINT messages_kind_check;
ALTER TABLE messages ADD CONSTRAINT messages_kind_check
    CHECK (kind IN ('text', 'pet_note', 'photo', 'plan', 'plan_response', 'gift'));
