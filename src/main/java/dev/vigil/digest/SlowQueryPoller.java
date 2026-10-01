package dev.vigil.digest;

import dev.vigil.collect.PerTargetRunner;
import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.spi.SlowQuerySource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Invariant: Periodically polls slow query sources across targets on isolated virtual threads.
 */
@Component
public class SlowQueryPoller {
  private final TargetRegistry registry;
  private final Map<EngineType, SlowQuerySource> sources;
  private final SlowQueryStats stats;
  private final PerTargetRunner runner = new PerTargetRunner("slowq");

  public SlowQueryPoller(TargetRegistry registry, List<SlowQuerySource> list, SlowQueryStats stats) {
    this.registry = registry;
    this.stats = stats;
    this.sources = list.stream().collect(Collectors.toMap(SlowQuerySource::engine, Function.identity()));
  }

  @PostConstruct
  void start() {
    runner.schedule(
        Duration.ofSeconds(30),
        () -> registry.all().stream().filter(t -> sources.containsKey(t.engine())).toList(),
        t -> sources.get(t.engine()).poll(t).forEach(o -> stats.record(t, o)));
  }

  @PreDestroy
  void stop() {
    runner.close();
  }
}
