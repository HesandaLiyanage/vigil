package dev.vigil.spi;

import dev.vigil.drill.DrillOptions;
import dev.vigil.model.DrillResult;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;

/**
 * Invariant: SPI contract for executing an automated restore verification drill.
 */
public interface RestoreDriller {
  EngineType engine();

  /**
   * Executes a restore drill against a throwaway container and returns verification outcome.
   */
  DrillResult run(ManagedTarget target, DrillOptions options);
}
