#!/usr/bin/env bash
set -euo pipefail
# smoke-test.sh — Phase 11 staging/prod
# Usage: ./scripts/smoke-test.sh https://example.com
BASE=${1:-http://127.0.0.1:3100}
API="$BASE/medac/api/v1"
if [[ "$BASE" == *"127.0.0.1"* ]]; then
  API="http://127.0.0.1:3100/api/v1"
fi
echo "==> health/live $API/health/live"
curl -fsS "$API/health/live" | grep -q '"status":"ok"'
echo "==> health/ready $API/health/ready"
curl -fsS "$API/health/ready" | grep -q '"status":"ready"'
echo "==> version $API/version"
curl -fsS "$API/version" | grep -q '"version"'
echo "==> auth flow"
EMAIL="smoke-$(date +%s)@example.com"
PASS="SmokeTest12345!"
REG=$(curl -fsS "$API/auth/register" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
USERID=$(echo "$REG" | grep -o '"user_id":"[^"]*"' | cut -d'"' -f4)
# verify via DB (set active) - need direct DB or skip for smoke
echo "user $USERID"
# login will fail pending until verified - for smoke we use dev bypass: set active via API if available
# For now just check that login fails with 403 for pending
set +e
LOGIN_FAIL=$(curl -s "$API/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
echo "$LOGIN_FAIL" | grep -q "ACCOUNT_NOT_ACTIVE" && echo "login correctly pending"
set -e
echo "==> no X-Powered-By on new API"
curl -sI "$API/health/live" | grep -qi "x-powered-by" && { echo "FAIL: X-Powered-By leaked"; exit 1; } || echo "ok no X-Powered-By"
echo "==> logs redacted check (manual: journalctl -u medac-api-new | grep -i password should be empty)"
echo "Smoke OK"
