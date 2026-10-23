# Deploying the Unkittered API on the GCP VM

The API runs on the **same Ubuntu 24.04 VM as commerce-orchestrator**, following
its conventions (native services under systemd, nginx in front, PostgreSQL 16
from apt, no Docker), and without touching any of its pieces:

| | commerce-orchestrator (existing) | Unkittered (new) |
|---|---|---|
| User / home | `dp`, `/opt/dp` | `unkittered`, `/opt/unkittered` |
| App port | 127.0.0.1:8000 | 127.0.0.1:**8081** |
| Database | `direct_publish` on PG 16 | `unkittered` on the **same** PG 16 (own role) |
| Cache | – | Redis (apt), 127.0.0.1:6379, 128 MB cap |
| Queue | – | ElasticMQ (SQS-compatible), 127.0.0.1:9324 |
| nginx site | `/etc/nginx/sites-available/dp` | `/etc/nginx/sites-available/unkittered` |
| Domain | the app's domain | its own, e.g. `api.unkittered.app` |
| Services | `dp-api`, `dp-worker`, `dp-scraper` | `unkittered-api`, `unkittered-elasticmq` |

Nothing new is exposed: only nginx (80/443) faces the internet, as before.

> **Memory.** The JVM is capped at 768 MB, ElasticMQ at 96 MB and Redis at
> 128 MB. On an e2-medium (4 GB) that sits next to the existing services, but
> watch `free -h` after the first week; move to e2-standard-2 (8 GB) if swap
> climbs. Building on the VM (manual deploys) briefly needs about 1 GB more,
> which CI deploys avoid.

## 0. Before you start

1. **Rotate the Firebase key.** An old Firebase service-account key is in this
   repo's public git history (commit `9ec8c49`). In the Google Cloud console
   (IAM → Service accounts → the `firebase-adminsdk` account → Keys) **delete
   that key** and create a new one. Never commit the new one; it goes on the VM
   (step 3).
2. **Pick the API domain** and add a DNS **A record** pointing at the VM's
   static external IP (`gcloud compute instances list` shows it). No domain
   yet? A free DuckDNS name works (as in commerce-orchestrator's GO_LIVE.md).
   M-Pesa callbacks and the apps need HTTPS, so do this before going live.

## 1. One-time VM setup

```bash
gcloud compute ssh <vm-name> --zone <zone>          # or your usual ssh
curl -fsSLO https://raw.githubusercontent.com/Charles-Wambua/unkittered-backend/main/deploy/setup-vm.sh
sudo bash setup-vm.sh api.unkittered.app            # your domain; "-" to skip nginx/TLS
```

The script is safe to re-run. It installs Java 21, Maven and Redis, creates the
`unkittered` user, database and role, installs ElasticMQ, the systemd units, an
nginx site plus a Certbot certificate, and the `unkittered-deploy` command. It
then builds and starts the API. It generates the DB password and JWT secret into
`/opt/unkittered/unkittered.env` (chmod 600).

Check it:

```bash
curl -s http://127.0.0.1:8081/actuator/health      # {"status":"UP"}
curl -s https://api.unkittered.app/actuator/health
systemctl status unkittered-api unkittered-elasticmq redis-server
```

## 2. Configure

`sudo nano /opt/unkittered/unkittered.env`, then
`sudo systemctl restart unkittered-api`. All settings are documented in
`deploy/unkittered.env.example`. For launch:

- `UNKITTERED_GOOGLE_CLIENT_IDS` / `UNKITTERED_APPLE_CLIENT_IDS`: to enable
  social sign-in.
- `UNKITTERED_MPESA_*`: Daraja credentials, with `UNKITTERED_MPESA_MODE=daraja`.
- Keep `UNKITTERED_BILLING_TRUST_CLIENT=false` and
  `UNKITTERED_LOG_RESET_CODES=false` in production.

## 3. Push notifications (optional)

```bash
sudo install -o unkittered -g unkittered -m 600 new-key.json /opt/unkittered/firebase-service-account.json
sudo systemctl restart unkittered-api
journalctl -u unkittered-api | grep -i firebase     # "push notifications enabled"
```

## 4. Deploying updates

**Automatic (recommended).** `.github/workflows/ci-deploy.yml` tests every push
and PR. On `main` it ships the tested jar to the VM and runs
`sudo unkittered-deploy --jar …`. To switch it on:

1. Make an SSH key for CI: `ssh-keygen -t ed25519 -f unkittered-ci -N ""`.
2. On the VM, add `unkittered-ci.pub` to `~/.ssh/authorized_keys` of the user
   you passed to `setup-vm.sh` (by default the one who ran it). That user may
   run only `sudo unkittered-deploy` without a password.
3. In GitHub, under Settings → Secrets and variables → Actions:
   - Secrets: `VM_HOST` (VM external IP), `VM_USER`, `VM_SSH_KEY` (contents of
     `unkittered-ci`).
   - Variables: `DEPLOY_ENABLED` = `true`.
4. Make sure port 22 is reachable from GitHub's runners. The GCP default
   `default-allow-ssh` rule allows it. If SSH is IAP-only, use the manual path
   or a self-hosted runner.

**Manual.** `ssh` in and run `sudo unkittered-deploy`. It pulls `main`, builds
on the VM and restarts.

Either way the deploy keeps the previous jar. If the new version isn't healthy
within 90 seconds it **rolls back automatically** and prints the last log lines.

Changed a systemd unit, the nginx template or `elasticmq.conf`? Re-run
`sudo bash /opt/unkittered/app/deploy/setup-vm.sh <domain>`.

## 5. Operating

| Task | Command |
|---|---|
| Logs | `journalctl -u unkittered-api -f` |
| Restart | `sudo systemctl restart unkittered-api` |
| Roll back by hand | `sudo cp /opt/unkittered/unkittered-api.jar.prev /opt/unkittered/unkittered-api.jar && sudo systemctl restart unkittered-api` |
| DB shell | `sudo -u postgres psql unkittered` |
| Backup DB | `sudo -u postgres pg_dump -Fc unkittered > unkittered-$(date +%F).dump` |
| Backup uploads | `sudo tar czf uploads-$(date +%F).tgz -C /opt/unkittered uploads` |

Flyway migrations run automatically on start. Back up before deploying anything
that adds a migration.

## 6. Point the apps at it

Build the Flutter app with:

```bash
flutter build appbundle --release \
  --dart-define=USE_MOCK_DATA=false \
  --dart-define=API_BASE_URL=https://api.unkittered.app/v1
```
