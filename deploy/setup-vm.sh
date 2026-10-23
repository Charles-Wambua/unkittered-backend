#!/usr/bin/env bash
# One-time setup of the Unkittered API on the existing GCP VM (the one already
# running commerce-orchestrator; Debian 12 or Ubuntu 24.04). Safe to re-run.
#
#   sudo bash setup-vm.sh <api-domain> [deploy-ssh-user]
#
#   api-domain       e.g. api.unkittered.app (DNS A record -> the VM's IP).
#                    Use "-" to skip nginx/TLS for now.
#   deploy-ssh-user  the account GitHub Actions logs in as (default: you).
#
# What it does — and does NOT touch the commerce-orchestrator ("dp") setup:
#   * system user `unkittered`, everything under /opt/unkittered
#   * JDK 21, Maven, Redis (localhost only) from apt
#   * ElasticMQ (SQS-compatible) as unkittered-elasticmq.service on 127.0.0.1:9324
#   * database `unkittered` + role on the existing PostgreSQL
#   * unkittered-api.service on 127.0.0.1:8081
#   * its own nginx site for <api-domain> (+ Certbot TLS)
#   * /usr/local/sbin/unkittered-deploy, runnable by the deploy user via sudo
set -euo pipefail

DOMAIN=${1:?"usage: sudo bash setup-vm.sh <api-domain|-> [deploy-ssh-user]"}
DEPLOY_USER=${2:-${SUDO_USER:-}}
REPO=${UNKITTERED_REPO:-https://github.com/Charles-Wambua/unkittered-backend.git}
ELASTICMQ_VERSION=1.6.11
APP_HOME=/opt/unkittered
SRC=$APP_HOME/app
ENV_FILE=$APP_HOME/unkittered.env

[[ $EUID -eq 0 ]] || { echo "Run with sudo." >&2; exit 1; }
say() { echo -e "\n==> $*"; }

say "Packages"
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  git curl gnupg redis-server postgresql-client openssl >/dev/null
# The deploy builds with Maven, so it needs a JDK. Ubuntu 24.04 ships
# OpenJDK 21; Debian 12 doesn't, so use Eclipse Temurin from Adoptium there.
if apt-cache show openjdk-21-jdk-headless &>/dev/null; then
  JDK_PKG=openjdk-21-jdk-headless
else
  curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | gpg --dearmor --yes -o /usr/share/keyrings/adoptium.gpg
  echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
    > /etc/apt/sources.list.d/adoptium.list
  apt-get update -qq
  JDK_PKG=temurin-21-jdk
fi
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$JDK_PKG" maven >/dev/null
# Maven pulls in the distro's default JRE too; make sure java/javac are 21.
update-alternatives --set java "$(update-alternatives --list java | grep 21 | head -1)" >/dev/null
JAVAC21=$(update-alternatives --list javac 2>/dev/null | grep 21 | head -1 || true)
[[ -n "$JAVAC21" ]] && update-alternatives --set javac "$JAVAC21" >/dev/null
java -version 2>&1 | head -1

say "System user and folders"
id unkittered &>/dev/null || useradd --system --home-dir "$APP_HOME" --shell /usr/sbin/nologin unkittered
install -d -o unkittered -g unkittered -m 0755 "$APP_HOME" "$APP_HOME/elasticmq"
install -d -o unkittered -g unkittered -m 0750 "$APP_HOME/uploads"

say "Source"
if [[ -d "$SRC/.git" ]]; then
  sudo -u unkittered git -C "$SRC" pull --ff-only --quiet
else
  sudo -u unkittered git clone --quiet "$REPO" "$SRC"
fi

say "Redis: localhost only, small memory cap"
sed -i 's/^#\?\s*bind .*/bind 127.0.0.1 -::1/' /etc/redis/redis.conf
grep -q '^maxmemory ' /etc/redis/redis.conf || echo 'maxmemory 128mb' >> /etc/redis/redis.conf
grep -q '^maxmemory-policy ' /etc/redis/redis.conf || echo 'maxmemory-policy allkeys-lru' >> /etc/redis/redis.conf
systemctl enable --now redis-server >/dev/null
systemctl restart redis-server

say "ElasticMQ $ELASTICMQ_VERSION"
if [[ ! -f "$APP_HOME/elasticmq/elasticmq-server.jar" ]]; then
  curl -fsSL -o "$APP_HOME/elasticmq/elasticmq-server.jar" \
    "https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-$ELASTICMQ_VERSION.jar"
fi
install -o unkittered -g unkittered -m 0644 "$SRC/deploy/elasticmq.conf" "$APP_HOME/elasticmq/elasticmq.conf"
chown unkittered:unkittered "$APP_HOME/elasticmq/elasticmq-server.jar"

say "Environment file"
if [[ ! -f "$ENV_FILE" ]]; then
  DB_PASS=$(openssl rand -hex 24)
  JWT=$(openssl rand -base64 48 | tr -d '\n')
  sed -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$DB_PASS|" \
      -e "s|^UNKITTERED_JWT_SECRET=.*|UNKITTERED_JWT_SECRET=$JWT|" \
      "$SRC/deploy/unkittered.env.example" > "$ENV_FILE"
  if [[ "$DOMAIN" != "-" ]]; then
    sed -i "s|^UNKITTERED_MPESA_CALLBACK_BASE_URL=.*|UNKITTERED_MPESA_CALLBACK_BASE_URL=https://$DOMAIN|" "$ENV_FILE"
  fi
  chown unkittered:unkittered "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "Created $ENV_FILE with a fresh DB password and JWT secret."
else
  echo "$ENV_FILE exists; leaving it alone."
fi
DB_PASS=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)

say "PostgreSQL: role + database 'unkittered' (the dp database is untouched)"
sudo -u postgres psql -v ON_ERROR_STOP=1 -q <<SQL
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'unkittered') THEN
    CREATE ROLE unkittered LOGIN PASSWORD '$DB_PASS';
  ELSE
    ALTER ROLE unkittered PASSWORD '$DB_PASS';
  END IF;
END \$\$;
SQL
sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='unkittered'" | grep -q 1 \
  || sudo -u postgres createdb -O unkittered unkittered

say "systemd units"
install -m 0644 "$SRC/deploy/systemd/unkittered-api.service" /etc/systemd/system/
install -m 0644 "$SRC/deploy/systemd/unkittered-elasticmq.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now unkittered-elasticmq >/dev/null
systemctl enable unkittered-api >/dev/null

say "Deploy command"
install -m 0755 -o root -g root "$SRC/deploy/deploy.sh" /usr/local/sbin/unkittered-deploy
if [[ -n "$DEPLOY_USER" ]]; then
  echo "$DEPLOY_USER ALL=(root) NOPASSWD: /usr/local/sbin/unkittered-deploy, /usr/local/sbin/unkittered-deploy *" \
    > /etc/sudoers.d/unkittered-deploy
  chmod 440 /etc/sudoers.d/unkittered-deploy
  visudo -cf /etc/sudoers.d/unkittered-deploy >/dev/null
  echo "$DEPLOY_USER may run 'sudo unkittered-deploy' without a password."
fi

if [[ "$DOMAIN" != "-" ]]; then
  say "nginx site for $DOMAIN"
  SITE=/etc/nginx/sites-available/unkittered
  if [[ -f "$SITE" ]] && grep -q 'managed by Certbot' "$SITE"; then
    echo "$SITE is Certbot-managed; leaving it alone."
  else
    sed "s/__DOMAIN__/$DOMAIN/g" "$SRC/deploy/nginx/unkittered.conf" > "$SITE"
  fi
  ln -sf "$SITE" /etc/nginx/sites-enabled/unkittered
  nginx -t
  systemctl reload nginx
  if command -v certbot >/dev/null; then
    say "TLS certificate"
    certbot --nginx -d "$DOMAIN" --redirect --non-interactive --agree-tos \
      --register-unsafely-without-email || echo "Certbot failed; check DNS for $DOMAIN, then re-run: sudo certbot --nginx -d $DOMAIN --redirect"
  fi
fi

say "First build and start"
/usr/local/sbin/unkittered-deploy

cat <<EOF

✅ Unkittered API is set up.
   Health:   curl -s http://127.0.0.1:8081/actuator/health
   Logs:     journalctl -u unkittered-api -f
   Config:   sudo nano $ENV_FILE   (then: sudo systemctl restart unkittered-api)
   Deploy:   sudo unkittered-deploy
EOF
