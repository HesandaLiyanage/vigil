package dev.vigil.mysql;

import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.QueryObservation;
import dev.vigil.spi.SlowQuerySource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Invariant: Incrementally polls mysql.slow_log for slow queries without reprocessing historical entries.
 */
@Component
public class MySqlSlowLogSource implements SlowQuerySource {
  private final TargetRegistry registry;
  private final ConcurrentMap<String, Timestamp> watermark = new ConcurrentHashMap<>();

  public MySqlSlowLogSource(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.MYSQL;
  }

  @Override
  public List<QueryObservation> poll(ManagedTarget t) throws Exception {
    try (Connection c = registry.jdbc(t.id())) {
      Timestamp since = watermark.get(t.id());
      if (since == null) { // first poll: start from "now" without any timezone arithmetic
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(start_time), NOW(6)) FROM mysql.slow_log")) {
          rs.next();
          watermark.put(t.id(), rs.getTimestamp(1));
        }
        return List.of();
      }
      List<QueryObservation> out = new ArrayList<>();
      try (PreparedStatement ps = c.prepareStatement("""
          SELECT start_time,
                 TIME_TO_SEC(query_time) * 1000000 + MICROSECOND(query_time) AS micros,
                 CONVERT(sql_text USING utf8mb4) AS sql_text
          FROM mysql.slow_log
          WHERE start_time > ? AND user_host NOT LIKE 'vigil%'
          ORDER BY start_time LIMIT 5000""")) {
        ps.setTimestamp(1, since);
        try (ResultSet rs = ps.executeQuery()) {
          Timestamp max = since;
          while (rs.next()) {
            out.add(new QueryObservation(rs.getString("sql_text"), rs.getLong("micros"), 1, Instant.now()));
            max = rs.getTimestamp("start_time");
          }
          watermark.put(t.id(), max);
        }
      }
      return out;
    }
  }
}
