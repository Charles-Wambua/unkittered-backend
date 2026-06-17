-- Seed discoverable profiles (the Unkittered dummy cast). These accounts have no
-- password hash, so they can be discovered/liked but not logged into.

INSERT INTO users (id, email, display_name, onboarding_complete, subscription_tier) VALUES
    ('11111111-1111-1111-1111-111111111111', 'sia@seed.unkittered',   'Sia',   true, 'free'),
    ('22222222-2222-2222-2222-222222222222', 'marco@seed.unkittered', 'Marco', true, 'free'),
    ('33333333-3333-3333-3333-333333333333', 'priya@seed.unkittered', 'Priya', true, 'free'),
    ('44444444-4444-4444-4444-444444444444', 'kai@seed.unkittered',   'Kai',   true, 'free');

INSERT INTO profiles (user_id, name, age, bio, location, latitude, longitude, is_verified, occupation, education, child_free_statement, card_quote, is_online) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Sia', 29,
     'Rescue dog mum, hiking addict, coffee snob. I chose a child-free life and I''d choose it again every single day.',
     'Nairobi', -1.2921, 36.8219, true, 'UX Designer', 'Wits University',
     'Child-free by choice', 'My dogs are my whole world.', true),
    ('22222222-2222-2222-2222-222222222222', 'Marco', 31,
     'Three cats, a rooftop garden, and zero plans for children. Looking for someone who gets that.',
     'Nairobi', -1.3000, 36.8000, true, 'Architect', 'UCT',
     'Intentionally child-free', 'Cats, good wine, and Sunday markets.', false),
    ('33333333-3333-3333-3333-333333333333', 'Priya', 30,
     'Vet by day, rabbit mum always. I have a parrot named Socrates who will judge you.',
     'Nairobi', -1.2700, 36.8100, false, 'Veterinarian', 'UP',
     'Happily child-free', 'Socrates will have opinions about you.', true),
    ('44444444-4444-4444-4444-444444444444', 'Kai', 33,
     'Three dogs and a veggie garden. My life is full and I love it that way.',
     'Nairobi', -1.3100, 36.8300, true, 'Architect', '',
     'Child-free, zero regrets', 'Three dogs and a garden — that''s the dream.', true);

INSERT INTO profile_interests (user_id, value) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Hiking'),
    ('11111111-1111-1111-1111-111111111111', 'Coffee'),
    ('11111111-1111-1111-1111-111111111111', 'Travel'),
    ('11111111-1111-1111-1111-111111111111', 'Yoga'),
    ('22222222-2222-2222-2222-222222222222', 'Cooking'),
    ('22222222-2222-2222-2222-222222222222', 'Photography'),
    ('22222222-2222-2222-2222-222222222222', 'Jazz'),
    ('22222222-2222-2222-2222-222222222222', 'Cycling'),
    ('33333333-3333-3333-3333-333333333333', 'Animals'),
    ('33333333-3333-3333-3333-333333333333', 'Reading'),
    ('33333333-3333-3333-3333-333333333333', 'Pilates'),
    ('33333333-3333-3333-3333-333333333333', 'Cooking'),
    ('44444444-4444-4444-4444-444444444444', 'Gardening'),
    ('44444444-4444-4444-4444-444444444444', 'Trail running'),
    ('44444444-4444-4444-4444-444444444444', 'Braai'),
    ('44444444-4444-4444-4444-444444444444', 'Surfing');

INSERT INTO profile_pets (user_id, value) VALUES
    ('11111111-1111-1111-1111-111111111111', '🐕 Rescue dog × 2'),
    ('22222222-2222-2222-2222-222222222222', '🐈 Three cats'),
    ('33333333-3333-3333-3333-333333333333', '🐰 Two bunnies'),
    ('33333333-3333-3333-3333-333333333333', '🦜 Parrot'),
    ('44444444-4444-4444-4444-444444444444', '🐕 Three dogs');

INSERT INTO profile_lifestyle_tags (user_id, value) VALUES
    ('11111111-1111-1111-1111-111111111111', '✈️ Travel lover'),
    ('11111111-1111-1111-1111-111111111111', '🌱 Outdoorsy'),
    ('22222222-2222-2222-2222-222222222222', '🏡 Homebody'),
    ('22222222-2222-2222-2222-222222222222', '🎭 Creative'),
    ('33333333-3333-3333-3333-333333333333', '🌿 Plant lover'),
    ('33333333-3333-3333-3333-333333333333', '📚 Bookworm'),
    ('44444444-4444-4444-4444-444444444444', '🌿 Outdoorsy'),
    ('44444444-4444-4444-4444-444444444444', '🏃 Active');
