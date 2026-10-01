package dev.vigil.drill;

import dev.vigil.collect.MetricsRecorder;
import dev.vigil.config.TargetRegistry;
import dev.vigil.model.DrillResult;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.Role;
import dev.vigil.spi.RestoreDriller;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Invariant: Serializes heavy restore drill executions across the fleet and reconciles recovery proof with persistent history and metrics.
 */
@Service
public class DrillService {
  private static final Logger log = LoggerFactory.getLogger(DrillService.class);

  private final TargetRegistry registry;
  private final Map<EngineType, RestoreDriller> drillers;
  private final DrillRepository repo;
  private final MetricsRecorder metrics;
  private final Semaphore oneAtATime = new Semaphore(1);

  public DrillService(TargetRegistry registry, List<RestoreDriller> drillers, DrillRepository repo, MetricsRecorder metrics) {
    this.registry = registry;
    this.repo = repo;
    this.metrics = metrics;
    this.drillers = drillers.stream().collect(Collectors.toMap(RestoreDriller::engine, Function.identity()));
  }

  /** Re-seed gauges from history so "last proven restorable" survives a restart. */
  @PostConstruct
  void hydrate() {
    for (ManagedTarget t : registry.all()) {
      repo.lastPassed(t.id()).ifPresent(i -> metrics.gauge("vigil.restore.last_success_epoch_seconds", t, i.getEpochSecond()));
    }
  }

  public DrillResult run(String targetId, DrillOptions opts) {
    ManagedTarget t = registry.get(targetId);
    RestoreDriller d = drillers.get(t.engine());
    if (d == null) {
      throw new UnsupportedOperationException("no restore drill for " + t.engine());
    }
    if (!oneAtATime.tryAcquire()) {
      throw new IllegalStateException("a drill is already running");
    }
    try {
      DrillResult r = d.run(t, opts);
      repo.save(r);
      metrics.gauge("vigil.restore.rto_seconds", t, r.rtoMs() / 1000.0);
      metrics.gauge("vigil.restore.last_status", t, r.status() == DrillResult.Status.PASSED ? 1 : 0);
      if (r.status() == DrillResult.Status.PASSED) {
        metrics.gauge("vigil.restore.last_success_epoch_seconds", t, r.startedAt().getEpochSecond());
      }
      log.info("drill {} {} rto={}ms", targetId, r.status(), r.rtoMs());
      return r;
    } finally {
      oneAtATime.release();
    }
  }

  @Scheduled(cron = "${vigil.drill.cron}")
  void scheduled() {
    for (ManagedTarget t : registry.all()) {
      if (drillers.containsKey(t.engine()) && t.dumpContainer() != null && t.role() != Role.REPLICA) {
        try {
          run(t.id(), DrillOptions.clean());
        } catch (Exception e) {
          log.warn("scheduled drill skipped for {}: {}", t.id(), e.toString());
        }
      }
    }
  }
}
