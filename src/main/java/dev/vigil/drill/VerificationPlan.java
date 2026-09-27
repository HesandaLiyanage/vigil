package dev.vigil.drill;

import java.util.List;
import java.util.Map;

/**
 * Invariant: Immutable specification of expected schemas, baseline row counts, checksums, and canary token for post-restore verification.
 */
public record VerificationPlan(
    List<String> schemas,
    Map<String, Long> rowCountsBefore,
    Map<String, String> checksumsBefore,
    String canaryToken,
    String canaryTable,
    double tolerance,
    ChecksumStrategy checksum) {

  public VerificationPlan {
    schemas = schemas == null ? List.of() : List.copyOf(schemas);
    rowCountsBefore = rowCountsBefore == null ? Map.of() : Map.copyOf(rowCountsBefore);
    checksumsBefore = checksumsBefore == null ? Map.of() : Map.copyOf(checksumsBefore);
  }
}
