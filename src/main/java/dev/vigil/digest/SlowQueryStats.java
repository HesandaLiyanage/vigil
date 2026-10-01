package dev.vigil.digest;

import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.QueryObservation;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Invariant: Aggregates query observations per target and bounds memory usage by fingerprint count.
 */
@Component
public class SlowQueryStats {
  private static final int MAX_FINGERPRINTS = 5_000; // bound memory; evict least-frequent
  private final ConcurrentMap<String, ConcurrentMap<String, FingerprintStats>> byTarget = new ConcurrentHashMap<>();
  private final SqlFingerprinter mysql = new SqlFingerprinter(SqlFingerprinter.Dialect.MYSQL);
  private final SqlFingerprinter pg = new SqlFingerprinter(SqlFingerprinter.Dialect.POSTGRES);

  public void record(ManagedTarget t, QueryObservation o) {
    SqlFingerprinter f = t.engine() == EngineType.POSTGRES ? pg : mysql;
    SqlFingerprinter.Fingerprint fp = f.fingerprint(o.sql());
    var m = byTarget.computeIfAbsent(t.id(), k -> new ConcurrentHashMap<>());
    if (m.size() >= MAX_FINGERPRINTS && !m.containsKey(fp.id())) {
      m.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().count()))
          .ifPresent(e -> m.remove(e.getKey()));
    }
    m.computeIfAbsent(fp.id(), k -> new FingerprintStats(fp, o.sql())).record(o.latencyMicros(), o.count());
  }

  /** Ranked like pt-query-digest: by total time consumed, not by single slowest execution. */
  public List<FingerprintStats.View> top(String targetId, int n) {
    var m = byTarget.getOrDefault(targetId, new ConcurrentHashMap<>());
    double grand = m.values().stream().mapToDouble(FingerprintStats::totalMicros).sum();
    return m.values().stream()
        .sorted(Comparator.comparingLong(FingerprintStats::totalMicros).reversed())
        .limit(n)
        .map(s -> s.view(grand))
        .toList();
  }
}
