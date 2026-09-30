package dev.vigil.audit;

import dev.vigil.access.AccessAuditService;
import dev.vigil.collect.MetricsRecorder;
import dev.vigil.config.TargetRegistry;
import dev.vigil.drill.DrillRepository;
import dev.vigil.drill.Sha256;
import dev.vigil.model.ManagedTarget;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Invariant: Generates cryptographically hashed markdown audit evidence documenting recovery proofs and access compliance.
 */
@Service
public class AuditReportService {
  private final DrillRepository drillRepo;
  private final AccessAuditService access;
  private final TargetRegistry registry;
  private final MetricsRecorder metrics;

  public AuditReportService(
      DrillRepository drillRepo,
      AccessAuditService access,
      TargetRegistry registry,
      MetricsRecorder metrics) {
    this.drillRepo = drillRepo;
    this.access = access;
    this.registry = registry;
    this.metrics = metrics;
  }

  public String markdown() {
    String host = "unknown";
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {}

    StringBuilder sb = new StringBuilder();
    sb.append("# Vigil Audit Report\n\n");
    sb.append("Generated: ").append(Instant.now()).append("\n");
    sb.append("Version: 0.1.0\n");
    sb.append("Host: ").append(host).append("\n\n");

    for (ManagedTarget t : registry.all()) {
      sb.append("## ").append(t.id()).append(" (").append(t.engine()).append(")\n\n");
      var drills = drillRepo.recent(t.id(), 5);
      if (!drills.isEmpty()) {
        sb.append("### Restore proof\n\n| Started (UTC) | Status | RTO (s) | Artifact SHA-256 |\n|---|---|---|---|\n");
        drills.forEach(d -> sb.append("| ").append(d.startedAt()).append(" | ").append(d.status()).append(" | ")
            .append(String.format("%.1f", d.rtoMs() / 1000.0)).append(" | `").append(d.artifactSha256()).append("` |\n"));
        sb.append("\n");
      }
      access.latest(t.id()).ifPresent(s -> {
        sb.append("### Access drift (scan at ").append(s.at()).append(")\n\n");
        if (s.findings().isEmpty()) {
          sb.append("No drift against policy.\n\n");
        } else {
          sb.append("| Severity | Type | Principal | Scope | Privilege |\n|---|---|---|---|---|\n");
          s.findings().forEach(f -> sb.append("| ").append(f.severity()).append(" | ").append(f.type()).append(" | ")
              .append(f.entry().principal()).append(" | ").append(f.entry().scope()).append(" | ")
              .append(f.entry().privilege()).append(" |\n"));
          sb.append("\n");
        }
      });
      metrics.current("vigil.db.replication_lag_heartbeat_ms", t.id()).ifPresent(v ->
          sb.append("Replication lag (heartbeat): ").append(String.format("%.0f ms", v)).append("\n\n"));
    }
    String body = sb.toString();
    return body + "---\nReport SHA-256: `" + Sha256.of(body.getBytes(StandardCharsets.UTF_8)) + "`\n";
  }
}
