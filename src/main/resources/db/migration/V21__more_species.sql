-- More animals: rabbits, fish, horses and small pets (hamsters, guinea pigs…).
ALTER TABLE pets DROP CONSTRAINT pets_species_check;
ALTER TABLE pets ADD CONSTRAINT pets_species_check
    CHECK (species IN ('dog', 'cat', 'rabbit', 'bird', 'fish', 'reptile', 'horse', 'small_pet', 'other'));

ALTER TABLE discovery_pref_species DROP CONSTRAINT discovery_pref_species_value_check;
ALTER TABLE discovery_pref_species ADD CONSTRAINT discovery_pref_species_value_check
    CHECK (value IN ('dog', 'cat', 'rabbit', 'bird', 'fish', 'reptile', 'horse', 'small_pet', 'other'));
