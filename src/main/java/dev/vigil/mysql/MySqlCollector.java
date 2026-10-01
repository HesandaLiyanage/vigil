package dev.vigil.mysql;

import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.HealthSample;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.Role;
import dev.vigil.spi.Collector;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Invariant: Safely collects read-only health, connection, storage, and replication metrics from MySQL targets.
 */
@Component
public class MySqlCollector implements Collector {
  private final TargetRegistry registry;
  private final HeartbeatReader heartbeat;

  public MySqlCollector(TargetRegistry registry, HeartbeatReader heartbeat) {
    this.registry = registry;
    this.heartbeat = heartbeat;
  }

  @Override
  public EngineType engine() {
    return EngineType.MYSQL;
  }

  @Override
  public HealthSample collect(ManagedTarget t) throws Exception {
    Map<String, Double> m = new LinkedHashMap<>();
    try (Connection c = registry.jdbc(t.id()); Statement st = c.createStatement()) {
      st.setQueryTimeout(5);
      Map<String, String> status = keyValue(st,
          "SHOW GLOBAL STATUS WHERE Variable_name IN "
              + "('Threads_connected','Threads_running','Slow_queries','Uptime','Aborted_connects')");
      Map<String, String> vars = keyValue(st, "SHOW GLOBAL VARIABLES WHERE Variable_name = 'max_connections'");

      m.put("connections_active", num(status.get("Threads_connected")));
      m.put("threads_running", num(status.get("Threads_running")));
      m.put("connections_max", num(vars.get("max_connections")));
      m.put("slow_queries_total", num(status.get("Slow_queries")));
      m.put("aborted_connects_total", num(status.get("Aborted_connects")));
      m.put("uptime_seconds", num(status.get("Uptime")));

      try (ResultSet rs = st.executeQuery(
          "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables")) {
        rs.next();
        m.put("storage_bytes", rs.getDouble(1));
      }

      if (t.role() == Role.REPLICA) {
        try (ResultSet rs = st.executeQuery("SHOW REPLICA STATUS")) {
          if (rs.next()) {
            m.put("replication_io_running", "Yes".equalsIgnoreCase(rs.getString("Replica_IO_Running")) ? 1.0 : 0.0);
            m.put("replication_sql_running", "Yes".equalsIgnoreCase(rs.getString("Replica_SQL_Running")) ? 1.0 : 0.0);
            long sbs = rs.getLong("Seconds_Behind_Source");
            m.put("replication_lag_reported_ms", rs.wasNull() ? Double.NaN : sbs * 1000.0);
          } else {
            m.put("replication_io_running", 0.0);
            m.put("replication_sql_running", 0.0);
          }
        }
        m.put("replication_lag_heartbeat_ms", heartbeat.readLagMillis(c));
      }
    }
    return new HealthSample(m);
  }

  private static Map<String, String> keyValue(Statement st, String sql) throws SQLException {
    Map<String, String> out = new HashMap<>();
    try (ResultSet rs = st.executeQuery(sql)) {
      while (rs.next()) {
        out.put(rs.getString(1), rs.getString(2));
      }
    }
    return out;
  }

  private static double num(String s) {
    return s == null ? Double.NaN : Double.parseDouble(s);
  }
}
