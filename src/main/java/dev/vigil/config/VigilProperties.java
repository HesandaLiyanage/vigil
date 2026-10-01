package dev.vigil.config;

import dev.vigil.model.ManagedTarget;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Invariant: Holds immutable, type-safe application configuration for the Vigil watchdog.
 */
@ConfigurationProperties(prefix = "vigil")
public record VigilProperties(
    List<ManagedTarget> targets,
    @DefaultValue("15s") Duration pollInterval,
    @DefaultValue("10s") Duration collectTimeout,
    @DefaultValue("1s") Duration heartbeatInterval,
    @DefaultValue Drill drill,
    @DefaultValue Policy policy) {

  public VigilProperties {
    targets = targets == null ? List.of() : List.copyOf(targets);
    drill = drill == null ? new Drill("0 0 */6 * * *", Path.of("./data/drills"), 0.02, "vigil_meta.canary") : drill;
    policy = policy == null ? new Policy(Path.of("./policy/grants-policy.yml")) : policy;
  }

  public record Drill(
      @DefaultValue("0 0 */6 * * *") String cron,
      @DefaultValue("./data/drills") Path workDir,
      @DefaultValue("0.02") double rowCountTolerance,
      @DefaultValue("vigil_meta.canary") String canaryTable) {}

  public record Policy(@DefaultValue("./policy/grants-policy.yml") Path file) {}
}
