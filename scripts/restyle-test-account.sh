#!/bin/bash
# Dresses up a test account (run on the VM): new name "Wanjiru", a stock
# profile photo, a cat and a parrot. Usage: bash scripts/restyle-test-account.sh EMAIL
# Login, email, age, bio and
# location are left alone. Suspension is lifted separately from the admin page.
set -euo pipefail
EMAIL=${1:?"usage: restyle-test-account.sh EMAIL"}
UP=/opt/unkittered/uploads

fetch() {
  local n
  n=$(openssl rand -hex 16).jpg
  sudo curl -sfL -o "$UP/$n" "https://images.unsplash.com/photo-$1?w=1080&q=80&fm=jpg"
  sudo chown unkittered:unkittered "$UP/$n"
  echo "$n"
}

# Store links in the same style as the other members' photos.
BASE=$(sudo -u postgres psql -d unkittered -tAc \
  "select substring(image_url from '^(.*)/files/') from profiles where image_url like '%/files/%' limit 1")
M=$BASE/files/$(fetch 1531123897727-8f129e1688ce)
C=$BASE/files/$(fetch 1514888286974-6c03e2ca1dba)
P=$BASE/files/$(fetch 1552728089-57bdde30beb3)
echo "photos: $M $C $P"

sudo -u postgres psql -d unkittered -v ON_ERROR_STOP=1 -v email="$EMAIL" -v m="$M" -v c="$C" -v p="$P" <<'SQL'
BEGIN;
SELECT id AS uid FROM users WHERE email = :'email' \gset
UPDATE users SET display_name = 'Wanjiru' WHERE id = :'uid';
UPDATE profiles SET name = 'Wanjiru', image_url = :'m' WHERE user_id = :'uid';
DELETE FROM profile_gallery WHERE user_id = :'uid';
INSERT INTO profile_gallery (user_id, value) VALUES (:'uid', :'c'), (:'uid', :'p');
DELETE FROM pets WHERE owner_id = :'uid';
INSERT INTO pets (owner_id, position, name, species, breed_and_age, energy, comes_on_dates, photo_url) VALUES
  (:'uid', 0, 'Mochi', 'cat',  'Tuxedo · 3 yrs',         2, false, :'c'),
  (:'uid', 1, 'Kiwi',  'bird', 'Green parakeet · 5 yrs', 4, false, :'p');
INSERT INTO pet_temperaments (pet_id, value) SELECT id, 'new_people' FROM pets WHERE owner_id = :'uid';
DELETE FROM profile_pets WHERE user_id = :'uid';
INSERT INTO profile_pets (user_id, value) VALUES (:'uid', U&'\+01F408 Mochi'), (:'uid', U&'\+01F99C Kiwi');
COMMIT;
SELECT u.email, u.display_name, u.suspended, p.name, p.image_url
  FROM users u JOIN profiles p ON p.user_id = u.id WHERE u.id = :'uid';
SELECT name, species, breed_and_age, photo_url FROM pets WHERE owner_id = :'uid' ORDER BY position;
SQL
for f in "$M" "$C" "$P"; do sudo ls -l "$UP/${f##*/files/}"; done
