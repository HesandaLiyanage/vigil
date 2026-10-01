#!/usr/bin/env bash
# Exit non-zero if any database container fails its basic liveness probe.
set -u
fail=0
check() { if eval "$2" >/dev/null 2>&1; then echo "OK    $1"; else echo "FAIL  $1"; fail=1; fi; }

check mysql-primary  "docker exec vigil-mysql-primary mysqladmin ping -uroot -prootpw"
check mysql-replica  "docker exec vigil-mysql-replica mysqladmin ping -uroot -prootpw"
check replica-io     "docker exec vigil-mysql-replica mysql -uroot -prootpw -e 'SHOW REPLICA STATUS\\G' | grep -q 'Replica_IO_Running: Yes'"
check replica-sql    "docker exec vigil-mysql-replica mysql -uroot -prootpw -e 'SHOW REPLICA STATUS\\G' | grep -q 'Replica_SQL_Running: Yes'"
check postgres       "docker exec vigil-postgres pg_isready -U vigil_admin -d vigil_demo"
check mongo          "docker exec vigil-mongo mongosh --quiet -u root -p rootpw --authenticationDatabase admin --eval 'db.adminCommand({ping:1}).ok' | grep -q 1"
check disk-usage     "[ \$((df --output=pcent / 2>/dev/null || df / | awk 'NR==2 {print \$(NF-1)}') | tail -1 | tr -dc '0-9') -lt 90 ]"
exit $fail
