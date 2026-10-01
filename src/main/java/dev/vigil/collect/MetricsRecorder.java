package dev.vigil.collect;

import dev.vigil.model.ManagedTarget;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Invariant: Maintains strong references to Micrometer gauges preventing weak reference GC eviction.
 */
@Component
public class MetricsRecorder {
  private final MeterRegistry registry;
  private final ConcurrentMap<String, AtomicReference<Double>> gauges = new ConcurrentHashMap<>();

  public MetricsRecorder(MeterRegistry registry) {
    this.registry = registry;
  }

  /** Micrometer holds gauge targets weakly, so we keep the strong reference in our map. */
  public void gauge(String name, ManagedTarget t, double value) {
    gauge(name, t, value, Map.of());
  }

  public void gauge(String name, ManagedTarget t, double value, Map<String, String> extraTags) {
    String key = name + "|" + t.id() + "|" + new TreeMap<>(extraTags);
    gauges.computeIfAbsent(key, k -> {
      AtomicReference<Double> ref = new AtomicReference<>(value);
      Gauge.Builder<AtomicReference<Double>> b = Gauge.builder(name, ref, AtomicReference::get)
          .tag("target", t.id())
          .tag("engine", t.engine().name().toLowerCase());
      extraTags.forEach(b::tag);
      b.register(registry);
      return ref;
    }).set(value);
  }

  public Optional<Double> current(String name, String targetId) {
    var ref = gauges.get(name + "|" + targetId + "|{}");
    return Optional.ofNullable(ref).map(AtomicReference::get);
  }
}
