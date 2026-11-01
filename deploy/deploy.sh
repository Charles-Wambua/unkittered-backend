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

# How long a start may take. This VM is short on memory and a cold start can
# pass 90 s; giving up too early rolled back a version that was still booting.
START_TIMEOUT=${UNKITTERED_START_TIMEOUT:-240}

healthy() {
  local deadline=$((SECONDS + START_TIMEOUT))
  while (( SECONDS < deadline )); do
    if curl -fsS "$HEALTH" 2>/dev/null | grep -q '"status":"UP"'; then return 0; fi
    # Crashed outright: no point waiting out the clock.
    if systemctl is-failed --quiet unkittered-api; then return 1; fi
    sleep 3
  done
  return 1
}

# The jar is only ever replaced while the service is stopped. Overwriting it
# under a running JVM makes classes "disappear" mid-start (NoClassDefFoundError),
# which is exactly what a rollback during a slow start used to do.
swap_in() {
  systemctl stop unkittered-api
  install -o unkittered -g unkittered -m 0644 "$1" "$JAR"
  systemctl start unkittered-api
}

echo "==> Installing $(basename "$NEW_JAR")"
[[ -f "$JAR" ]] && cp -f "$JAR" "$JAR.prev"
echo "==> Restarting unkittered-api (allowing up to ${START_TIMEOUT}s)"
swap_in "$NEW_JAR"
if healthy; then
  echo "✅ Deployed and healthy."
  exit 0
fi

echo "❌ New version is not healthy. Last log lines:" >&2
journalctl -u unkittered-api -n 60 --no-pager >&2 || true
if [[ -f "$JAR.prev" ]]; then
  echo "==> Rolling back to the previous jar" >&2
  swap_in "$JAR.prev"
  healthy && echo "↩️  Rolled back; previous version is serving." >&2
fi
exit 1
