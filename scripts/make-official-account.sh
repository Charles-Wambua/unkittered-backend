#!/bin/bash
# Makes an account the official Unkittered account (run on the VM, after a
# deploy that includes migration V34):
#
#   bash scripts/make-official-account.sh EMAIL
#
# Name "Unkittered", the paw logo as its photo, Biscuit (the mascot, wearing the
# Gold crown) as its pet, and the official tick everywhere it appears. It also
# drops out of the discover deck and map. The login is left exactly as it is.
set -euo pipefail
EMAIL=${1:?"usage: make-official-account.sh EMAIL"}
UP=/opt/unkittered/uploads
HERE=$(cd "$(dirname "$0")" && pwd)

# Copy an image into storage under a random name, like a normal upload.
put() {
  local n
  n=$(openssl rand -hex 16).png
  sudo install -o unkittered -g unkittered -m 0644 "$HERE/official/$1" "$UP/$n"
  echo "$n"
}

# Store links in the same style as the other members' photos.
BASE=$(sudo -u postgres psql -d unkittered -tAc \
  "select substring(image_url from '^(.*)/files/') from profiles where image_url like '%/files/%' limit 1")
LOGO=$BASE/files/$(put logo.png)
PET=$BASE/files/$(put biscuit.png)

sudo -u postgres psql -d unkittered -v ON_ERROR_STOP=1 -v email="$EMAIL" -v logo="$LOGO" -v pet="$PET" <<'SQL'
BEGIN;
SELECT id AS uid FROM users WHERE email = :'email' \gset
UPDATE users SET display_name = 'Unkittered' WHERE id = :'uid';
UPDATE profiles SET
  name = 'Unkittered',
  image_url = :'logo',
  is_official = true,
  is_verified = true,
  bio = 'The official Unkittered account 🐾 Updates, new features and the occasional good boy. Questions or ideas? Message us right here.'
WHERE user_id = :'uid';
DELETE FROM profile_gallery WHERE user_id = :'uid';
INSERT INTO profile_gallery (user_id, value) VALUES (:'uid', :'pet');
DELETE FROM pets WHERE owner_id = :'uid';
INSERT INTO pets (owner_id, position, name, species, breed_and_age, energy, comes_on_dates, photo_url)
  VALUES (:'uid', 0, 'Biscuit', 'dog', 'Chief Wag Officer · Golden pup', 5, true, :'pet');
INSERT INTO pet_temperaments (pet_id, value)
  SELECT id, t FROM pets, unnest(ARRAY['new_people', 'cats', 'small_dogs', 'big_dogs']) AS t WHERE owner_id = :'uid';
DELETE FROM profile_pets WHERE user_id = :'uid';
INSERT INTO profile_pets (user_id, value) VALUES (:'uid', U&'\+01F415 Biscuit');
COMMIT;
SELECT u.email, u.display_name, p.name, p.is_official, p.is_verified, p.image_url
  FROM users u JOIN profiles p ON p.user_id = u.id WHERE u.id = :'uid';
SELECT name, species, breed_and_age, photo_url FROM pets WHERE owner_id = :'uid';
SQL
