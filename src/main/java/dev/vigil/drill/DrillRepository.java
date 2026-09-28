package dev.vigil.drill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigil.model.CheckResult;
import dev.vigil.model.DrillResult;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Invariant: Persists and queries drill history in Vigil's local H2 store so recovery proof survives restarts.
 */
@Repository
public class DrillRepository {
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public DrillRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void save(DrillResult r) {
    jdbc.sql("""
        INSERT INTO drill_run (id, target_id, started_at, status, backup_ms, backup_bytes,
          container_start_ms, restore_ms, verify_ms, rto_ms, artifact_sha256, checks_json, error)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""")
        .params(r.id(), r.targetId(), Timestamp.from(r.startedAt()), r.status().name(), r.backupMs(),
            r.backupBytes(), r.containerStartMs(), r.restoreMs(), r.verifyMs(), r.rtoMs(),
            r.artifactSha256(), toJson(r.checks()), r.error())
        .update();
  }

  public List<DrillResult> recent(String targetId, int limit) {
    return jdbc.sql("SELECT * FROM drill_run WHERE target_id = ? ORDER BY started_at DESC LIMIT ?")
        .params(targetId, limit)
        .query((rs, i) -> new DrillResult(
            rs.getString("id"), rs.getString("target_id"), rs.getTimestamp("started_at").toInstant(),
            DrillResult.Status.valueOf(rs.getString("status")), rs.getLong("backup_ms"),
            rs.getLong("backup_bytes"), rs.getLong("container_start_ms"), rs.getLong("restore_ms"),
            rs.getLong("verify_ms"), rs.getLong("rto_ms"), rs.getString("artifact_sha256"),
            fromJson(rs.getString("checks_json")), rs.getString("error")))
        .list();
  }

  public Optional<Instant> lastPassed(String targetId) {
    return jdbc.sql("SELECT MAX(started_at) FROM drill_run WHERE target_id = ? AND status = 'PASSED'")
        .param(targetId)
        .query((rs, i) -> rs.getTimestamp(1))
        .optional()
        .flatMap(ts -> Optional.ofNullable(ts).map(Timestamp::toInstant));
  }

  private String toJson(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<CheckResult> fromJson(String s) {
    if (s == null || s.isBlank()) {
      return List.of();
    }
    try {
      return json.readValue(s, new TypeReference<List<CheckResult>>() {});
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
