#!/bin/bash
# Lets shared profile links (https://<site>/u/<id>) open in the Android app
# (run on the VM). Sets UNKITTERED_ANDROID_CERT_SHA256 in the env file and
# restarts the API; safe to re-run.
#
#   bash scripts/enable-app-links.sh                 # the debug key the testers' APKs use
#   bash scripts/enable-app-links.sh AA:BB:...       # another key (several: comma-separated)
#
# Release APKs are signed with this machine's debug key until Play signing is
# set up; add the Play app-signing key's fingerprint then.
set -euo pipefail
FP=${1:-50:9A:21:2C:08:76:F2:3A:EE:86:DD:D2:DF:AA:22:27:F2:2A:BD:D6:29:1F:68:BC:5E:05:B2:01:6F:F6:DE:36}
ENV=/opt/unkittered/unkittered.env

sudo sed -i '/^UNKITTERED_ANDROID_CERT_SHA256=/d' "$ENV"
printf '\nUNKITTERED_ANDROID_CERT_SHA256=%s\n' "$FP" | sudo tee -a "$ENV" > /dev/null
sudo systemctl restart unkittered-api
echo "restarted; waiting for the API..."
for i in $(seq 1 48); do
  if curl -fs -o /dev/null http://localhost:8081/actuator/health; then echo "API is up"; break; fi
  sleep 5
done
curl -s http://localhost:8081/.well-known/assetlinks.json | head -c 300; echo
