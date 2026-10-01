#!/usr/bin/env bash
set -euo pipefail
API=${VIGIL_API:-http://localhost:7080/api}
show() { jq '{status, rtoMs, failed: [.checks[] | select(.passed|not) | .name]}'; }

echo "### 1. Healthy backup"
curl -s -X POST "$API/drills/mysql-primary" | show
echo; echo "### 2. Same backup, truncated mid-file"
curl -s -X POST "$API/drills/mysql-primary?corrupt=TRUNCATE" | show
echo; echo "### 3. Last proven restorable"
curl -s "$API/status" | jq '.[] | select(.id=="mysql-primary") | {id, lastProvenRestorable}'
