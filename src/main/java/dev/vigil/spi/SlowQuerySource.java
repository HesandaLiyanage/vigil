package dev.vigil.spi;

import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.QueryObservation;
import java.util.List;

/**
 * Invariant: SPI contract for polling slow query execution observations since last poll.
 */
public interface SlowQuerySource {
  EngineType engine();

  /**
   * Returns observations since the previous poll for this target. Stateful per target.
   */
  List<QueryObservation> poll(ManagedTarget target) throws Exception;
}
