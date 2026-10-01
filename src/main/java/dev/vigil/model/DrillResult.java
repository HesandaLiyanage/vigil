package dev.vigil.model;

import java.time.Instant;
import java.util.List;

/**
 * Invariant: Encapsulates the immutable outcome, metrics, and verification steps of a restore drill.
 */
public record DrillResult(
    String id,
    String targetId,
    Instant startedAt,
    Status status,
    long backupMs,
    long backupBytes,
    long containerStartMs,
    long restoreMs,
    long verifyMs,
    long rtoMs,
    String artifactSha256,
    List<CheckResult> checks,
    String error) {

  public enum Status {
    PASSED,
    FAILED_RESTORE,
    FAILED_VERIFICATION,
    ERROR
  }

  public DrillResult {
    checks = checks == null ? List.of() : List.copyOf(checks);
  }
}
