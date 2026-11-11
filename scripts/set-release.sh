#!/bin/bash
# Sets the release the app's "Update available" banner offers (run on the VM):
#
#   bash scripts/set-release.sh 1.0.5 7 "Live chat, calmer Today, photo viewer."
#
# Same as the admin page's Releases form. The APK must already be at
# /files/unkittered-<version>.apk (see publish-build.sh).
set -euo pipefail
VERSION=${1:?"usage: set-release.sh VERSION BUILD [NOTES]"}
BUILD=${2:?"usage: set-release.sh VERSION BUILD [NOTES]"}
NOTES=${3:-}
URL="https://unkittered.directpublish.duckdns.org/files/unkittered-$VERSION.apk"

sudo -u postgres psql -d unkittered -v ON_ERROR_STOP=1 \
  -v version="$VERSION" -v build="$BUILD" -v url="$URL" -v notes="$NOTES" <<'SQL'
INSERT INTO app_release (id, version, build, url, notes, updated_at)
VALUES (1, :'version', :build, :'url', :'notes', now())
ON CONFLICT (id) DO UPDATE SET version = EXCLUDED.version, build = EXCLUDED.build,
  url = EXCLUDED.url, notes = EXCLUDED.notes, updated_at = now();
SELECT version, build, url, left(notes, 60) AS notes FROM app_release;
SQL
