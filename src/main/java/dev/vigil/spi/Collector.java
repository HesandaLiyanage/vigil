package dev.vigil.spi;

import dev.vigil.model.EngineType;
import dev.vigil.model.HealthSample;
import dev.vigil.model.ManagedTarget;

/**
 * Invariant: SPI contract for fast, non-mutating database health sample collection.
 */
public interface Collector {
  EngineType engine();

  /**
   * One fast, read-only health sample. Must not mutate the target.
   */
  HealthSample collect(ManagedTarget target) throws Exception;
}
