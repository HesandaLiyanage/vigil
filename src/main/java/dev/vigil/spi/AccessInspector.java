package dev.vigil.spi;

import dev.vigil.access.AccessEntry;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import java.util.Set;

/**
 * Invariant: SPI contract for capturing a normalized snapshot of permissions and roles.
 */
public interface AccessInspector {
  EngineType engine();

  /**
   * Normalized snapshot of who can do what on the target database.
   */
  Set<AccessEntry> snapshot(ManagedTarget target) throws Exception;
}
