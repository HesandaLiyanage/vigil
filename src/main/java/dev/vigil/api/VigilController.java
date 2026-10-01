package dev.vigil.api;

import dev.vigil.access.AccessAuditService;
import dev.vigil.access.DriftFinding;
import dev.vigil.audit.AuditReportService;
import dev.vigil.collect.MetricsRecorder;
import dev.vigil.config.TargetRegistry;
import dev.vigil.drill.CorruptionMode;
import dev.vigil.drill.DrillOptions;
import dev.vigil.drill.DrillRepository;
import dev.vigil.drill.DrillService;
import dev.vigil.model.DrillResult;
import dev.vigil.digest.FingerprintStats;
import dev.vigil.digest.SlowQueryStats;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invariant: Exposes REST API endpoints for fleet status, drill execution, recovery history, slow queries, and access audit reports.
 */
@RestController
@RequestMapping("/api")
public class VigilController {
  private final TargetRegistry registry;
  private final DrillService drills;
  private final DrillRepository drillRepo;
  private final SlowQueryStats slow;
  private final AccessAuditService access;
  private final AuditReportService report;
  private final MetricsRecorder metrics;

  public VigilController(
      TargetRegistry registry,
      DrillService drills,
      DrillRepository drillRepo,
      SlowQueryStats slow,
      AccessAuditService access,
      AuditReportService report,
      MetricsRecorder metrics) {
    this.registry = registry;
    this.drills = drills;
    this.drillRepo = drillRepo;
    this.slow = slow;
    this.access = access;
    this.report = report;
    this.metrics = metrics;
  }

  public record TargetStatus(
      String id,
      String engine,
      boolean up,
      Instant lastProvenRestorable,
      Long lastRtoMs,
      Double heartbeatLagMs) {}

  @GetMapping("/status")
  public List<TargetStatus> status() {
    return registry.all().stream().map(t -> {
      var last = drillRepo.recent(t.id(), 1).stream().findFirst();
      return new TargetStatus(
          t.id(),
          t.engine().name(),
          metrics.current("vigil.db.up", t.id()).map(v -> v == 1.0).orElse(false),
          drillRepo.lastPassed(t.id()).orElse(null),
          last.map(DrillResult::rtoMs).orElse(null),
          metrics.current("vigil.db.replication_lag_heartbeat_ms", t.id()).orElse(null));
    }).toList();
  }

  @PostMapping("/drills/{targetId}")
  public DrillResult drill(
      @PathVariable String targetId,
      @RequestParam(defaultValue = "NONE") CorruptionMode corrupt) {
    return drills.run(targetId, new DrillOptions(corrupt));
  }

  @GetMapping("/drills")
  public List<DrillResult> history(
      @RequestParam String targetId,
      @RequestParam(defaultValue = "10") int limit) {
    return drillRepo.recent(targetId, limit);
  }

  @GetMapping("/slow-queries/{targetId}")
  public List<FingerprintStats.View> slowQueries(
      @PathVariable String targetId,
      @RequestParam(defaultValue = "10") int top) {
    return slow.top(targetId, top);
  }

  @GetMapping("/access/{targetId}/drift")
  public List<DriftFinding> drift(
      @PathVariable String targetId,
      @RequestParam(defaultValue = "false") boolean refresh) throws Exception {
    if (refresh) {
      return access.scan(registry.get(targetId)).findings();
    }
    var opt = access.latest(targetId);
    if (opt.isPresent()) {
      return opt.get().findings();
    }
    return access.scan(registry.get(targetId)).findings();
  }

  @GetMapping(value = "/audit/report", produces = "text/markdown")
  public String auditReport() {
    return report.markdown();
  }
}
