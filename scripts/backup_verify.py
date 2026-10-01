#!/usr/bin/env python3
"""Ask Vigil whether every database has a recent proven restore. Exit 1 if not (cron-friendly)."""
import json, os, sys, urllib.request
from datetime import datetime, timedelta, timezone

URL = os.environ.get("VIGIL_URL", "http://localhost:7080/api/status")
MAX_AGE = timedelta(hours=int(sys.argv[1]) if len(sys.argv) > 1 else 24)

def main() -> int:
    with urllib.request.urlopen(URL, timeout=10) as r:
        targets = json.load(r)
    now, bad = datetime.now(timezone.utc), []
    for t in targets:
        if t.get("lastProvenRestorable") is None:
            continue  # engines without drills (mongo, oracle, replicas) are skipped on purpose
        ts = datetime.fromisoformat(t["lastProvenRestorable"].replace("Z", "+00:00"))
        if now - ts > MAX_AGE:
            bad.append(f'{t["id"]}: last proven restorable {ts.isoformat()}')
    print(json.dumps({"checked": len(targets), "stale": bad}, indent=2))
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main())
