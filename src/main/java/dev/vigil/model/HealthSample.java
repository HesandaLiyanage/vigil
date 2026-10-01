package dev.vigil.model;

import java.util.Map;

/**
 * Invariant: Holds an immutable snapshot of named health metrics collected from a target.
 */
public record HealthSample(Map<String, Double> metrics) {
  public HealthSample {
    metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
  }
}
