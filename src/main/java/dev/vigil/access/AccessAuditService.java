package dev.vigil.access;

import dev.vigil.collect.MetricsRecorder;
import dev.vigil.collect.PerTargetRunner;
import dev.vigil.config.TargetRegistry;
import dev.vigil.config.VigilProperties;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.AccessInspector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Invariant: Periodically scans target databases for access grants and compares against policy to detect drift.
 */
@Service
public class AccessAuditService {
  public record Scan(Instant at, Set<AccessEntry> snapshot, List<DriftFinding> findings) {}

  private final VigilProperties props;
  private final TargetRegistry registry;
  private final Map<EngineType, AccessInspector> inspectors;
  private final DriftDetector detector;
  private final MetricsRecorder metrics;
  private final ConcurrentMap<String, Scan> latest = new ConcurrentHashMap<>();
  private final PerTargetRunner runner = new PerTargetRunner("access");
  private volatile AccessPolicy policy;

  public AccessAuditService(
      VigilProperties props,
      TargetRegistry registry,
      List<AccessInspector> list,
      DriftDetector detector,
      MetricsRecorder metrics) throws IOException {
    this.props = props;
    this.registry = registry;
    this.detector = detector;
    this.metrics = metrics;
    this.inspectors = list.stream().collect(Collectors.toMap(AccessInspector::engine, Function.identity()));
    if (Files.exists(props.policy().file())) {
      this.policy = AccessPolicy.load(props.policy().file());
    } else {
      this.policy = new AccessPolicy(Map.of());
    }
  }

  @PostConstruct
  void start() {
    runner.schedule(
        Duration.ofMinutes(15),
        () -> registry.all().stream().filter(t -> inspectors.containsKey(t.engine())).toList(),
        this::scan);
  }

  @PreDestroy
  void stop() {
    runner.close();
  }

  public Scan scan(ManagedTarget t) throws Exception {
    if (Files.exists(props.policy().file())) {
      try {
        this.policy = AccessPolicy.load(props.policy().file());
      } catch (Exception ignored) {}
    }
    AccessInspector inspector = inspectors.get(t.engine());
    if (inspector == null) {
      throw new UnsupportedOperationException("no access inspector for " + t.engine());
    }
    Set<AccessEntry> snap = inspector.snapshot(t);
    var tp = policy.targets() != null ? policy.targets().get(t.id()) : null;
    List<DriftFinding> findings = tp == null ? List.of() : detector.diff(snap, tp);
    Scan s = new Scan(Instant.now(), snap, findings);
    latest.put(t.id(), s);
    for (DriftFinding.Severity sev : DriftFinding.Severity.values()) {
      long n = findings.stream().filter(f -> f.severity() == sev).count();
      metrics.gauge("vigil.access.drift_findings", t, n, Map.of("severity", sev.name()));
    }
    return s;
  }

  public Optional<Scan> latest(String targetId) {
    return Optional.ofNullable(latest.get(targetId));
  }

  public void reloadPolicy() throws IOException {
    if (Files.exists(props.policy().file())) {
      this.policy = AccessPolicy.load(props.policy().file());
    }
  }
}
