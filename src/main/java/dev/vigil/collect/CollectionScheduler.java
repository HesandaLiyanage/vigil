package dev.vigil.collect;

import dev.vigil.config.TargetRegistry;
import dev.vigil.config.VigilProperties;
import dev.vigil.model.EngineType;
import dev.vigil.model.HealthSample;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.Collector;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Invariant: Coordinates non-blocking, isolated collection cycles across all targets with enforced timeouts and JFR events.
 */
@Component
public class CollectionScheduler {
  private static final Logger log = LoggerFactory.getLogger(CollectionScheduler.class);

  private final VigilProperties props;
  private final TargetRegistry registry;
  private final Map<EngineType, Collector> collectors;
  private final MetricsRecorder metrics;
  private final MeterRegistry meters;
  private final PerTargetRunner runner = new PerTargetRunner("collect");
  private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

  public CollectionScheduler(
      VigilProperties props,
      TargetRegistry registry,
      List<Collector> list,
      MetricsRecorder metrics,
      MeterRegistry meters) {
    this.props = props;
    this.registry = registry;
    this.metrics = metrics;
    this.meters = meters;
    this.collectors = list.stream().collect(Collectors.toMap(Collector::engine, Function.identity()));
  }

  @PostConstruct
  void start() {
    runner.schedule(props.pollInterval(), registry::all, this::collectOne);
  }

  @PreDestroy
  void stop() {
    runner.close();
    io.shutdownNow();
  }

  private void collectOne(ManagedTarget t) {
    Collector c = collectors.get(t.engine());
    if (c == null) return;
    CollectionCycleEvent ev = new CollectionCycleEvent();
    ev.target = t.id();
    ev.engine = t.engine().name();
    ev.begin();
    try {
      HealthSample s = CompletableFuture.supplyAsync(() -> {
            try {
              return c.collect(t);
            } catch (Exception e) {
              throw new CompletionException(e);
            }
          }, io)
          .orTimeout(props.collectTimeout().toMillis(), TimeUnit.MILLISECONDS)
          .join();
      s.metrics().forEach((k, v) -> metrics.gauge("vigil.db." + k, t, v));
      metrics.gauge("vigil.db.up", t, 1);
      ev.success = true;
      ev.metricCount = s.metrics().size();
    } catch (Exception e) {
      metrics.gauge("vigil.db.up", t, 0);
      meters.counter("vigil.collection.failures", "target", t.id()).increment();
      log.warn("collect failed for {}: {}", t.id(), e.getCause() != null ? e.getCause().toString() : e.toString());
    } finally {
      ev.commit();
    }
  }
}
