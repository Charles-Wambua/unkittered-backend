#!/bin/bash
# Publishes an APK pushed to the temporary `builds` branch (run on the VM):
#
#   bash scripts/publish-build.sh unkittered-1.0.3.apk
#
# It lands in the uploads folder, so it downloads from /files/<name>.
# Delete the `builds` branch on GitHub afterwards.
set -euo pipefail
NAME=${1:?"usage: publish-build.sh FILE_NAME"}
APP=/opt/unkittered/app
DEST=/opt/unkittered/uploads/$NAME

sudo -u unkittered git -C "$APP" fetch -q origin builds
sudo -u unkittered git -C "$APP" show "FETCH_HEAD:$NAME" | sudo tee "$DEST" > /dev/null
sudo chown unkittered: "$DEST"
ls -l "$DEST"
sha256sum "$DEST"
