#!/usr/bin/env bash
# Deploys the Unkittered API on the VM. Installed by setup-vm.sh as
# /usr/local/sbin/unkittered-deploy (root-owned), so CI can run it via sudo.
#
#   sudo unkittered-deploy                 # pull main, build on the VM, restart
#   sudo unkittered-deploy --jar FILE.jar  # install a jar built elsewhere (CI)
#
# Keeps the previous jar and rolls back automatically if the new one doesn't
# come up healthy.
set -euo pipefail

APP_HOME=/opt/unkittered
SRC=$APP_HOME/app
JAR=$APP_HOME/unkittered-api.jar
HEALTH=http://127.0.0.1:8081/actuator/health
BRANCH=${UNKITTERED_BRANCH:-main}

[[ $EUID -eq 0 ]] || { echo "Run with sudo." >&2; exit 1; }

NEW_JAR=""
if [[ "${1:-}" == "--jar" ]]; then
  NEW_JAR=${2:?"--jar needs a path"}
  [[ -f "$NEW_JAR" ]] || { echo "No such jar: $NEW_JAR" >&2; exit 1; }
else
  echo "==> Pulling $BRANCH"
  sudo -u unkittered git -C "$SRC" fetch --quiet origin "$BRANCH"
  sudo -u unkittered git -C "$SRC" checkout --quiet "$BRANCH"
  sudo -u unkittered git -C "$SRC" reset --quiet --hard "origin/$BRANCH"
  echo "==> Building ($(sudo -u unkittered git -C "$SRC" log -1 --format='%h %s'))"
  sudo -u unkittered -H bash -c "cd '$SRC' && mvn -B -q -DskipTests package"
  NEW_JAR=$(ls "$SRC"/target/unkittered-api-*.jar | grep -v '\.original$' | head -1)
fi

echo "==> Installing $(basename "$NEW_JAR")"
[[ -f "$JAR" ]] && cp -f "$JAR" "$JAR.prev"
install -o unkittered -g unkittered -m 0644 "$NEW_JAR" "$JAR"

healthy() {
  for _ in $(seq 1 45); do
    if curl -fsS "$HEALTH" 2>/dev/null | grep -q '"status":"UP"'; then return 0; fi
    sleep 2
  done
  return 1
}

echo "==> Restarting unkittered-api"
systemctl restart unkittered-api
if healthy; then
  echo "✅ Deployed and healthy."
  exit 0
fi

echo "❌ New version is not healthy. Last log lines:" >&2
journalctl -u unkittered-api -n 40 --no-pager >&2 || true
if [[ -f "$JAR.prev" ]]; then
  echo "==> Rolling back to the previous jar" >&2
  cp -f "$JAR.prev" "$JAR"
  systemctl restart unkittered-api
  healthy && echo "↩️  Rolled back; previous version is serving." >&2
fi
exit 1
