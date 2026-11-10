#!/bin/bash
# Renames the official Unkittered account's pet (run on the VM):
#
#   bash scripts/rename-official-pet.sh Shenron
#
# Updates the pet itself and the short "🐕 Name" label older app versions show.
set -euo pipefail
NAME=${1:?"usage: rename-official-pet.sh NEW_NAME"}

sudo -u postgres psql -d unkittered -v ON_ERROR_STOP=1 -v name="$NAME" <<'SQL'
BEGIN;
UPDATE pets SET name = :'name'
  WHERE owner_id IN (SELECT user_id FROM profiles WHERE is_official) AND position = 0;
UPDATE profile_pets SET value = U&'\+01F415 ' || :'name'
  WHERE user_id IN (SELECT user_id FROM profiles WHERE is_official);
COMMIT;
SELECT p.name AS account, pet.name AS pet, pet.breed_and_age
  FROM profiles p JOIN pets pet ON pet.owner_id = p.user_id WHERE p.is_official;
SQL
