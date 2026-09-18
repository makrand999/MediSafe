# Deployment — Staging & Production

> Plan §23, §24 Phase 11

## Topology
```
Internet → nginx :80/:443 → /medac/api/v1 → 127.0.0.1:3100 (medac-api, medac user)
                         → /medac        → 127.0.0.1:3002 (legacy, to be retired)
                         → /rebuildx     → 127.0.0.1:3001
                         → /             → 127.0.0.1:8080 (vps-portal)
Medac API → localhost:5432 PostgreSQL (medac, medac_migration)
```
Fastify exposes `/api/v1` internally; nginx strips `/medac` prefix. Public base: `https://<domain>/medac/api/v1`.

## Prerequisites (Phase A — read-only discovery done)
- `lsb_release -a` `df -h` `free -h` `systemctl status` `ss -tlnp` `cat /etc/nginx/sites-enabled/default` `cat /etc/systemd/system/medac*` `psql --version` — all inspected 2026-08-18, backed up to `/tmp/nginx-default.bak.*` before changes.

## Host hardening (Phase B — checklist, execute incrementally, verify second terminal before closing first)
1. `adduser deploy` `usermod -aG sudo deploy` `mkdir -p /home/deploy/.ssh` `cat ~/server2/ssh22.pub >> authorized_keys` `chmod 600`
2. `ufw allow 22/tcp` `ufw allow 80/tcp` `ufw allow 443/tcp` `ufw enable`
3. `apt install fail2ban` `systemctl enable fail2ban`
4. Edit `/etc/ssh/sshd_config`: `PermitRootLogin no` `PasswordAuthentication no` `AllowUsers deploy` — `sshd -t` `systemctl reload sshd` — verify `ssh -i ssh22.pem deploy@host` in second terminal before `exit`.
5. `useradd --system --no-create-home medac` `chown -R medac:medac /opt/medac /var/lib/medac` `chmod 750 /opt/medac/secrets`.

## Base services (Phase C)
```bash
apt update && apt install -y postgresql postgresql-contrib
systemctl enable postgresql && systemctl start postgresql
sudo -u postgres createuser medac --pwprompt
sudo -u postgres createdb medac -O medac
# roles per migrations/0002_roles.up.sql — run as postgres superuser:
psql -U postgres -d medac -f migrations/0002_roles.up.sql
mkdir -p /opt/medac/{server,env,secrets} /var/lib/medac/exports /var/log/medac
openssl genpkey -algorithm RSA -out /opt/medac/secrets/access-private.pem 2048
openssl pkey -in /opt/medac/secrets/access-private.pem -pubout -out /opt/medac/secrets/access-public.pem
chmod 600 /opt/medac/secrets/* && chown medac:medac /opt/medac/secrets/*
cat > /opt/medac/env/api.env <<'EOS'
NODE_ENV=production
HOST=127.0.0.1
PORT=3100
PUBLIC_BASE_URL=https://example.com
DATABASE_URL=postgres://medac:***@localhost:5432/medac
...
EOS
chmod 600 /opt/medac/env/api.env
```

## Staging (Phase D)
- Hostname `staging.example.com` separate DB `medac_staging`
- `npm ci && npm run build && npm run db:migrate && npm run db:seed` (synthetic only)
- `systemctl enable medac-api --now` `systemctl enable medac-worker --now`
- `nginx -t && systemctl reload nginx`
- Smoke: `scripts/smoke-test.sh staging.example.com`

## Production (Phase E)
- Domain `example.com` `certbot --nginx -d example.com -d staging.example.com` `systemctl status certbot.timer`
- `tar -czf server.tar.gz --exclude=server/node_modules server && scp server.tar.gz host:/tmp/ && tar -xzf ... -C /opt/medac && chown -R medac:medac /opt/medac/server && sudo -u medac npm ci --production && sudo -u medac npm run build && sudo -u medac npm run db:migrate` (as `medac_migration` role)
- `systemctl restart medac-api && systemctl restart medac-worker` `curl -s https://example.com/medac/api/v1/health/live` `.../health/ready` `.../version`
- Verify existing app: `curl -s https://example.com/rebuildx` `200`

## Rollback
- `systemctl stop medac-api && cp -r /opt/medac/server /opt/medac/server.bad && tar -xzf /tmp/prev-server.tar.gz -C /opt/medac && chown -R medac:medac /opt/medac/server && sudo -u medac npm run build && systemctl start medac-api`
- DB migrations are backward-compatible for 1 version; otherwise restore from encrypted backup per `BACKUP_RESTORE.md`.

## Smoke test (Phase F)
```bash
./scripts/smoke-test.sh https://169.58.196.107 # current IP before domain
# Checks: /health/live 200, /health/ready 200, /version, auth register/login, POST /patients, POST /medications, POST /schedules, POST /dose-events, GET /reports/adherence, Muse Spark label, conversation, proposal confirm, no X-Powered-By, logs redacted.
```
See `scripts/smoke-test.sh`.
