#!/bin/bash
# Clears dating-profile leftovers (interests, the card prompt) from the
# official Unkittered account (run on the VM):
#
#   bash scripts/tidy-official-profile.sh
set -euo pipefail
sudo -u postgres psql -d unkittered -v ON_ERROR_STOP=1 <<'SQL'
BEGIN;
DELETE FROM profile_interests WHERE user_id IN (SELECT user_id FROM profiles WHERE is_official);
UPDATE profiles SET card_quote = '' WHERE is_official;
COMMIT;
SELECT p.name, p.card_quote, (SELECT count(*) FROM profile_interests i WHERE i.user_id = p.user_id) AS interests
  FROM profiles p WHERE p.is_official;
SQL
