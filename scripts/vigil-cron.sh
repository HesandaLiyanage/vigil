#!/usr/bin/env bash
# Cron wrapper: serialized (flock), logged, non-zero exit on any failure.
# crontab:  */15 * * * * /opt/vigil/scripts/vigil-cron.sh
set -u
cd "$(dirname "$0")"
LOG="${VIGIL_LOG:-/tmp/vigil-cron.log}"
NOW() { date -Is 2>/dev/null || date "+%Y-%m-%dT%H:%M:%S%z"; }

if command -v flock >/dev/null 2>&1; then
  exec 9>/tmp/vigil-cron.lock
  flock -n 9 || { echo "$(NOW) previous run still active" >> "$LOG"; exit 0; }
else
  LOCKDIR=/tmp/vigil-cron.lock.d
  if ! mkdir "$LOCKDIR" 2>/dev/null; then
    echo "$(NOW) previous run still active" >> "$LOG"; exit 0
  fi
  trap 'rm -rf "$LOCKDIR"' EXIT
fi

{
  echo "=== $(NOW) ==="
  ./health_check.sh;  rc1=$?
  python3 ./backup_verify.py 24; rc2=$?
  echo "health=$rc1 backup_verify=$rc2"
} >> "$LOG" 2>&1

[ "$rc1" -eq 0 ] && [ "$rc2" -eq 0 ]
