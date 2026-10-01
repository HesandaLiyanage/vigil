# Vigil: Architectural Decisions & Design Notes

This document details the architectural decisions (ADRs), tradeoffs, and operational realities underlying **Vigil**, a restore-verified database fleet watchdog.

---

## 1. Architectural Decision Records (ADRs)

| # | Decision | Reason | Tradeoff |
|---|---|---|---|
| **1** | **Engine capabilities are separate SPIs** (`Collector`, `SlowQuerySource`, `AccessInspector`, `RestoreDriller`) | Different databases provide fundamentally different capabilities. Oracle and MongoDB support monitoring and access review; MySQL and PostgreSQL support full restore verification. Segregated interfaces prevent hollow `UnsupportedOperationException` stubs. | More interface types to maintain, but zero runtime surprises or deceptive contract promises. |
| **2** | **Virtual thread per target per cycle**, driven by a single platform ticker and an in-flight guard | Fleet polling is overwhelmingly network I/O-bound. One degraded, stalled, or partition-isolated database must never delay, block, or pile up work on healthy targets. | JDBC drivers must avoid pinning carrier threads (`synchronized` blocks on blocking I/O); requires verification via JFR events. |
| **3** | **Synthetic heartbeat rows** over `Seconds_Behind_Source` | Direct measurement of true end-to-end commit-to-visibility lag (network transmission, relay staging, apply thread execution), even when the SQL thread is idle. | Resolution is bounded by the heartbeat write frequency (1s); requires write privileges on metadata schema; assumes synchronized system clocks (NTP). |
| **4** | **Restore drills into throwaway containers**, never the live database | Safe by construction. Completely eliminates any danger of destructive write collisions or split-brain on live production clusters. | Measured container RTO is a lower-bound execution metric; it differs from bare-metal production RTO due to dataset size and hardware differences. |
| **5** | **Pre-dump canary token insertion** | Proves the backup artifact is at least as fresh as the drill initiation timestamp, and catches clean silent truncations that row-count tolerance bounds might miss. | Requires write access to `vigil_meta.canary` on the source instance. |
| **6** | **Hand-written single-pass SQL tokenizer**, not an external AST parser library | Fast (~400,000 ops/sec), zero-dependency, and specifically tuned for literal-scrubbing and query shape aggregation without parser overhead. | Not a full SQL AST validator; documented edge cases on complex nested subqueries. |
| **7** | **Embedded H2 for drill history, concurrent in-memory structures for hot stats** | Zero-ops embedded state. Drill results and proven recovery milestones survive Vigil restarts, while hot latency histograms avoid persistent disk write contention. | H2 is local to the Vigil node; not designed for active-active multi-instance HA. |
| **8** | **Pluggable backup source** (`RestoreDriller.createBackup`) | Allows seamless drop-in adaptation from containerized logical dumps (`mysqldump`, `pg_dump`) to enterprise storage snapshots or S3 backup objects. | In default demo mode, Vigil verifies backups it extracts itself rather than external third-party scheduler artifacts. |

---

## 2. Why Heartbeat Beats `Seconds_Behind_Source`

MySQL's native `Seconds_Behind_Source` (or `Seconds_Behind_Master`) is one of the most notoriously misleading metrics in database operations:

1. **How it actually works**: MySQL computes the difference between the timestamp of the event currently being executed by the SQL apply thread and the timestamp recorded when that event was originally committed on the source.
2. **Idle false zeros**: When no transactions are being executed (the replication stream is idle), `Seconds_Behind_Source` reads `0`. If the I/O replication thread stalls or the network partition disconnects, the SQL thread finishes existing relay logs and reports `0`, masking a severe, widening replication gap.
3. **Connection failures report NULL**: If replication breaks or encounters an error, the metric becomes `NULL`, breaking arithmetic thresholds in naive monitoring setups unless explicitly handled.
4. **Step jumps on long transactions**: A single large DDL or bulk batch transaction will report 0 until execution begins, then suddenly jump to the duration of that entire transaction, giving false-positive latency spikes followed by abrupt drops.
5. **Multi-threaded replication ambiguity**: In parallel replication configurations (`replica_parallel_workers > 0`), the reported lag fluctuates depending on which worker thread is evaluated.

### Vigil's Heartbeat Mechanism
- **Writer**: `HeartbeatWriter` writes a millisecond-precision UTC timestamp to `vigil_meta.heartbeat (id=1, ts=NOW(6))` on the primary every 1 second.
- **Reader**: `HeartbeatReader` queries `SELECT ts FROM vigil_meta.heartbeat WHERE id = 1` on the replica.
- **Calculation**: $\text{lag} = \text{now}() - \text{ts}_{\text{replicated}}$.
- **Why it wins**: It measures the *actual wall-clock duration* required for a physical byte write to traverse the primary engine, binary log, network socket, relay log, and replica storage engine into queryable visibility.

*Caveats*: Bounded by heartbeat write interval (1s resolution); requires write grant on primary metadata schema; relies on synchronized host clocks (NTP drift $< 5$ms).

---

## 3. What a Restore Drill Proves — And What It Cannot Prove

### What Vigil Proves
1. **Physical Artifact Validity**: The dump file or backup archive is not truncated, bit-rotted, or syntactically invalid.
2. **Engine Compatibility**: The database engine binaries accept the schema definitions, table options, and encoding.
3. **Execution RTO**: Measures the precise elapsed time to spawn fresh infrastructure, stream the backup artifact, and execute full restoration.
4. **Data Integrity & Consistency**:
   - Verification of mandatory table existence.
   - Row counts match source tables within tolerance ($\le 2\%$).
   - Canary token inserted milliseconds prior to backup is present in the restored instance.
   - Deterministic row checksums (`CHECKSUM TABLE` / SHA-256 aggregations) match between source and restored data.

### What Vigil Cannot Prove (Honest DBA Reality)
1. **Production-Scale RTO**: Restoring a 10 GB slice on NVMe containers in 12 seconds does not guarantee a 5 TB database will restore in 4 hours on production storage with active SAN contention.
2. **Point-In-Time Recovery (PITR)**: Logical drills test baseline state restoration; they do not validate WAL/binlog replay up to an arbitrary millisecond transaction timestamp.
3. **Full Row Exhaustion**: For multi-billion row tables, comparing full-table checksums can saturate source I/O. Toleranced sample checks and canary tokens mitigate clean truncations, but silent single-bit flips in unchecked cold rows require background scrubbing.
4. **Authentication & Security Objects**: `mysqldump --databases` and standard `pg_restore` intentionally exclude cluster-level security definitions (`mysql.user`, `pg_roles`, global tablespaces).
5. **Application-Level Semantic Correctness**: Restoration proves database relational integrity, not that the application code logic can function with orphaned domain entities.
6. **Human and Runbook Readiness**: Automated drills verify technical mechanisms; they cannot verify whether on-call engineers have correct access tokens, runbooks, and communication channels during an incident.
7. **KMS / HSM Encryption Key Availability**: If transparent data encryption (TDE) keys are rotated or inaccessible outside production VPCs, restores will fail in external DR zones.

*Operational Guidance*: Continuous throwaway drills must be layered with periodic full-scale disaster recovery rehearsals.

---

## 4. Virtual Threading Architecture & Pinning Verification

### Polling Engine
Fleet monitoring is fundamentally network I/O-bound. Traditional thread-pool architectures face two failure modes:
1. **Thread starvation**: If several target databases become unresponsive, pool threads block on socket reads, preventing healthy databases from being monitored.
2. **Work pile-ups**: If a target query hangs longer than the cycle interval, new polling threads accumulate indefinitely, leading to out-of-memory errors.

### The Vigil Solution: `PerTargetRunner`
```java
// Platform ticker fires every 15s; each target dispatches on a Virtual Thread
workers = Executors.newVirtualThreadPerTaskExecutor();
ticker.scheduleWithFixedDelay(() -> {
  for (ManagedTarget t : targets.get()) {
    if (!inFlight.add(name + ":" + t.id())) continue; // In-flight guard
    workers.submit(() -> {
      try { task.run(t); }
      finally { inFlight.remove(name + ":" + t.id()); }
    });
  }
}, 0, every.toMillis(), TimeUnit.MILLISECONDS);
```
- **Isolated Virtual Threads**: Unbounded virtual thread allocation allows thousands of target checks without carrier thread thread exhaustion.
- **In-Flight Guard**: A `ConcurrentHashMap.newKeySet()` prevents concurrent cycles from piling up on a slow or hanging database.

### Virtual Thread Pinning Verification
Carrier thread pinning occurs when code executes blocking operations inside a `synchronized` block or native frame. Vigil was verified against carrier pinning using:
1. JVM startup flag: `-Djdk.tracePinnedThreads=full`
2. Java Flight Recorder (JFR): Inspected via `jfr print --events jdk.VirtualThreadPinned vigil.jfr`
3. Result: **0 pinned thread events recorded** during continuous fleet collection and drill execution.

---

## 5. SQL Fingerprinter Design & Edge Cases

### Design
- **Single-Pass Character Scanner**: `dev.vigil.digest.SqlFingerprinter` processes raw SQL strings in $O(N)$ time with minimal allocation.
- **Dialect Handling**:
  - `MYSQL`: Recognizes `#` line comments, backtick identifiers, and treats double quotes as string literals.
  - `POSTGRES`: Treats double quotes as table/column identifiers, recognizes `$1`, `$2` positional parameters.
- **List & Values Collapsing**:
  - Regex collapses variable-length `IN (1, 2, 3)` to `in (?+)`.
  - Collapses multi-row inserts `VALUES (1, 'a'), (2, 'b')` to `values (?+)`.
- **Latency Histogram**: Backed by `org.HdrHistogram.ConcurrentHistogram` (1 µs to 1 hour range, 3 significant figures) with zero lock contention on writes.

### Known Limitations
- Does not build a full AST: `IN (SELECT id FROM t)` is not collapsed into a scalar placeholder.
- Negative numbers like `-42` are normalized as `-?` rather than single parameter `?`.
- Does not extract Common Table Expression (CTE) alias structures.

---

## 6. Slow Log vs `pg_stat_statements`

| Dimension | MySQL Slow Query Log (`mysql.slow_log`) | PostgreSQL (`pg_stat_statements`) |
|---|---|---|
| **Data Nature** | Discrete, individual slow query execution events. | Pre-aggregated cumulative query execution statistics. |
| **Observation** | True individual execution latency ($\mu$s resolution). | Cumulative calls, total time; Vigil computes interval delta means. |
| **Percentiles** | **True percentiles** of distinct query executions. | **Percentiles of interval means** across collection ticks. |
| **Watermarking** | High-water mark on `start_time` timestamp. | In-memory delta tracking of `(calls, total_exec_time)` per `queryid`. |

---

## 7. Scope of Oracle Coverage

Vigil covers Oracle (tested on Oracle Database 23c Free) for **Fleet Monitoring & Access Governance only**:
- **Health & Metrics**: Active/inactive sessions, processes limit utilization, storage allocation, and tablespace utilization percentage (`DBA_TABLESPACE_USAGE_METRICS`).
- **Access Review**: Audit of system privileges (`DBA_SYS_PRIVS`) and granted roles (`DBA_ROLE_PRIVS`) for all non-system (`oracle_maintained = 'N'`) principals.
- **Why RMAN Drills are Out of Scope for v1**: An automated RMAN restore drill requires creating an auxiliary instance, physical block allocation, archive log sequencing, and `DUPLICATE TARGET DATABASE` or `RESTORE VALIDATE` routines requiring root/host access, which exceeds lightweight container boundaries.
