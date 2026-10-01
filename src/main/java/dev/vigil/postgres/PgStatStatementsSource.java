package dev.vigil.postgres;

import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.QueryObservation;
import dev.vigil.spi.SlowQuerySource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Invariant: Computes query call and execution time deltas from pg_stat_statements across polling intervals.
 */
@Component
public class PgStatStatementsSource implements SlowQuerySource {
  private record Prev(long calls, double totalMs) {}
  private final TargetRegistry registry;
  private final ConcurrentMap<String, Map<Long, Prev>> prev = new ConcurrentHashMap<>();

  public PgStatStatementsSource(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.POSTGRES;
  }

  /**
   * pg_stat_statements is cumulative and pre-aggregated, so we take deltas between polls and record the
   * interval MEAN with weight = delta calls. Percentiles are therefore percentiles of interval means,
   * NOT of individual executions. Documented limitation (contrast with MySQL slow log).
   */
  @Override
  public List<QueryObservation> poll(ManagedTarget t) throws Exception {
    Map<Long, Prev> last = prev.computeIfAbsent(t.id(), k -> new HashMap<>());
    boolean baseline = last.isEmpty();
    List<QueryObservation> out = new ArrayList<>();
    try (Connection c = registry.jdbc(t.id());
         Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("""
           SELECT queryid, query, calls, total_exec_time FROM pg_stat_statements
           WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
             AND query NOT ILIKE '%pg_stat_statements%' AND query NOT ILIKE '%vigil_meta%'""")) {
      while (rs.next()) {
        long id = rs.getLong(1);
        String q = rs.getString(2);
        long calls = rs.getLong(3);
        double total = rs.getDouble(4);
        Prev p = last.put(id, new Prev(calls, total));
        if (p == null) {
          if (baseline) continue;
          p = new Prev(0, 0);
        }
        if (calls <= p.calls()) continue; // no new calls, or stats were reset
        long dCalls = calls - p.calls();
        double meanMs = (total - p.totalMs()) / dCalls;
        out.add(new QueryObservation(q, Math.round(meanMs * 1000), dCalls, Instant.now()));
      }
    }
    return out;
  }
}
