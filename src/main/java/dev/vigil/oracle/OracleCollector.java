package dev.vigil.oracle;

import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.HealthSample;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.Collector;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Invariant: Collects Oracle database user sessions, process limits, max tablespace utilization, and data file storage.
 */
@Component
public class OracleCollector implements Collector {
  private final TargetRegistry registry;

  public OracleCollector(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.ORACLE;
  }

  @Override
  public HealthSample collect(ManagedTarget t) throws Exception {
    Map<String, Double> m = new LinkedHashMap<>();
    try (Connection c = registry.jdbc(t.id()); Statement st = c.createStatement()) {
      st.setQueryTimeout(5);

      try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM v$session WHERE type='USER' AND status='ACTIVE'")) {
        if (rs.next()) {
          m.put("sessions_active", rs.getDouble(1));
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM v$session WHERE type='USER'")) {
        if (rs.next()) {
          m.put("sessions_total", rs.getDouble(1));
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT TO_NUMBER(value) FROM v$parameter WHERE name='processes'")) {
        if (rs.next()) {
          m.put("sessions_max", rs.getDouble(1));
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT MAX(used_percent) FROM dba_tablespace_usage_metrics")) {
        if (rs.next()) {
          double usedPct = rs.getDouble(1);
          m.put("tablespace_used_pct_max", rs.wasNull() ? Double.NaN : usedPct);
        }
      }

      try (ResultSet rs = st.executeQuery("SELECT SUM(bytes) FROM dba_data_files")) {
        if (rs.next()) {
          double bytes = rs.getDouble(1);
          m.put("storage_bytes", rs.wasNull() ? Double.NaN : bytes);
        }
      }
    }
    return new HealthSample(m);
  }
}
