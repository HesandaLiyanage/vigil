package dev.vigil.postgres;

import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.HealthSample;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.Role;
import dev.vigil.spi.Collector;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Invariant: Collects PostgreSQL performance, connection limits, buffer cache hits, and replication lag metrics.
 */
@Component
public class PostgresCollector implements Collector {
  private final TargetRegistry registry;

  public PostgresCollector(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.POSTGRES;
  }

  @Override
  public HealthSample collect(ManagedTarget t) throws Exception {
    Map<String, Double> m = new LinkedHashMap<>();
    try (Connection c = registry.jdbc(t.id()); Statement st = c.createStatement()) {
      st.setQueryTimeout(5);

      try (ResultSet rs = st.executeQuery(
          "SELECT count(*) FROM pg_stat_activity WHERE state='active' AND pid <> pg_backend_pid()")) {
        if (rs.next()) {
          m.put("connections_active", rs.getDouble(1));
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_stat_activity")) {
        if (rs.next()) {
          m.put("connections_total", rs.getDouble(1));
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT current_setting('max_connections')::int")) {
        if (rs.next()) {
          m.put("connections_max", rs.getDouble(1));
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT pg_database_size(current_database())")) {
        if (rs.next()) {
          m.put("storage_bytes", rs.getDouble(1));
        }
      }

      try (ResultSet rs = st.executeQuery(
          "SELECT blks_hit::float / NULLIF(blks_hit + blks_read, 0) FROM pg_stat_database WHERE datname = current_database()")) {
        if (rs.next()) {
          double ratio = rs.getDouble(1);
          m.put("cache_hit_ratio", rs.wasNull() ? Double.NaN : ratio);
        }
      }

      try (ResultSet rs = st.executeQuery(
          "SELECT deadlocks FROM pg_stat_database WHERE datname = current_database()")) {
        if (rs.next()) {
          m.put("deadlocks_total", rs.getDouble(1));
        }
      }

      if (t.role() == Role.REPLICA) {
        try (ResultSet rs = st.executeQuery(
            "SELECT EXTRACT(EPOCH FROM now() - pg_last_xact_replay_timestamp()) * 1000")) {
          if (rs.next()) {
            double lag = rs.getDouble(1);
            m.put("replication_lag_ms", rs.wasNull() ? Double.NaN : lag);
          }
        }
      }
    }
    return new HealthSample(m);
  }
}
