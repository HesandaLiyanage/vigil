#!/usr/bin/env bash
# Generates slow, varied queries so the digest has something to group.
set -euo pipefail
run() { docker exec vigil-mysql-primary mysql -uroot -prootpw vigil_demo -e "$1" >/dev/null 2>&1; }
for i in $(seq 1 120); do
  run "SELECT * FROM orders WHERE total_cents > $((RANDOM % 90000)) ORDER BY created_at DESC LIMIT 20;" &
  run "SELECT c.name, COUNT(*) FROM customers c JOIN orders o ON o.customer_id = c.id
       WHERE c.id IN ($((RANDOM%900)),$((RANDOM%900)),$((RANDOM%900))) GROUP BY c.name;" &
  run "SELECT SLEEP(0.07), id FROM customers WHERE id = $((RANDOM%1000));" &
  (( i % 6 == 0 )) && wait
done
wait
echo "load generated"
