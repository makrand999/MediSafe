#!/usr/bin/env bash
set -euo pipefail
# backup-check.sh — Phase 11 §19
BACKUP_DIR="/var/backups"
AGE_LIMIT=172800 # 48h
SIZE_MIN=10240
latest=$(ls -t "$BACKUP_DIR"/medac-*.sql.gz.enc 2>/dev/null | head -1 || true)
if [[ -z "$latest" ]]; then
  echo "CRITICAL: no backup found in $BACKUP_DIR" >&2
  exit 2
fi
age=$(( $(date +%s) - $(stat -c %Y "$latest") ))
size=$(stat -c %s "$latest")
if (( age > AGE_LIMIT )); then
  echo "CRITICAL: backup $latest age ${age}s > ${AGE_LIMIT}s" >&2
  exit 2
fi
if (( size < SIZE_MIN )); then
  echo "CRITICAL: backup $latest size ${size} < ${SIZE_MIN}" >&2
  exit 2
fi
echo "OK: $latest age=${age}s size=${size}"
