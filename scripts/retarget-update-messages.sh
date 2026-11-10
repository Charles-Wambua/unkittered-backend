#!/bin/bash
# Points the official account's "new test version" chat messages at a newer
# build (run on the VM). Example, after uploading unkittered-1.0.2.apk:
#
#   bash scripts/retarget-update-messages.sh 1.0.1 1.0.2
#
# Only messages sent by the official account that link to the old APK are
# changed: "(OLD)" becomes "(NEW)" and the link points at the new file. They
# are marked as edited, so the app shows it. Past the app's 15-minute edit
# window this is the only way to fix a link that's already been sent.
set -euo pipefail
OLD=${1:?"usage: retarget-update-messages.sh OLD_VERSION NEW_VERSION"}
NEW=${2:?"usage: retarget-update-messages.sh OLD_VERSION NEW_VERSION"}

sudo -u postgres psql -d unkittered -v ON_ERROR_STOP=1 -v old="$OLD" -v new="$NEW" <<'SQL'
UPDATE messages m
SET body = replace(replace(m.body, '(' || :'old' || ')', '(' || :'new' || ')'),
                   'unkittered-' || :'old' || '.apk', 'unkittered-' || :'new' || '.apk'),
    edited_at = now()
FROM profiles p
WHERE p.user_id = m.sender_id AND p.is_official
  AND m.deleted_at IS NULL
  AND m.body LIKE '%unkittered-' || :'old' || '.apk%';
SELECT left(m.body, 70) AS starts, m.edited_at FROM messages m
  JOIN profiles p ON p.user_id = m.sender_id AND p.is_official
  WHERE m.body LIKE '%unkittered-' || :'new' || '.apk%';
SQL
